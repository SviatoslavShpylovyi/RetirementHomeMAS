from __future__ import annotations

import os
from typing import Any, Dict

import requests
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from langchain_openai import ChatOpenAI
from pydantic import ValidationError

from schemas import ActivityProposalMessage, MobilityLevel,GenerateActivityRequest

load_dotenv()
MAIN_APP_URL = os.getenv("MAIN_APP_URL", "http://localhost:8080").rstrip("/")
OPENAI_MODEL = os.getenv("OPENAI_MODEL")
app = FastAPI(title="RetirementHome activity generator")
SYSTEM_PROMPT = """
You generate retirement-home activity proposals as structured JSON only.

The JSON must match the provided Pydantic schema exactly.

The output must contain:
- eventType
- source
- payload
- explanation

The payload must be only one Activity object with:
- id
- name
- type
- maxParticipants
- requiredMobilityLevel

Do not generate:
- room
- time slot
- participants
- resources
- approval flag
- full event proposal
- warnings field

The JADE ScenarioAgent will later transform this Activity into an EventRequest/EventProposal.

Business rules:
- Prefer activities matching resident preferences from the provided context.
- Prefer safe activities for residents who are willingToParticipate.
- Mobility rule: LOW activity is safest and can be joined by everyone; MEDIUM requires at least MEDIUM; HIGH requires HIGH.
- Prefer ids like LC-ACT-001, LC-ACT-002, LC-ACT-003, etc.
- The explanation must be maximum two sentences.
""".strip()
def _validate_against_request(
    activity_message: ActivityProposalMessage,
    request: GenerateActivityRequest,
) -> None:
    activity = activity_message.payload

    if request.preferredEvents and activity.type not in request.preferredEvents:
        raise ValueError("Generated activity type does not match preferredEvents")

    if request.preferredMobility and activity.requiredMobilityLevel not in request.preferredMobility:
        raise ValueError("Generated mobility level does not match preferredMobility " + activity.requiredMobilityLevel)

    if request.preferredNumOfParticipants:
        values = request.preferredNumOfParticipants

        if len(values) == 1:
            if activity.maxParticipants != values[0]:
                raise ValueError("Generated maxParticipants does not match preferredNumOfParticipants")

        elif len(values) >= 2:
            min_participants = min(values)
            max_participants = max(values)

            if not min_participants <= activity.maxParticipants <= max_participants:
                raise ValueError("Generated maxParticipants is outside preferredNumOfParticipants range")

def _build_model():
    if not OPENAI_MODEL:
        raise RuntimeError("LLMMODEl is not set!!!")
    return ChatOpenAI(
        model=OPENAI_MODEL,
        temperature=0,
    ).with_structured_output(ActivityProposalMessage)

def generate_activity(req: GenerateActivityRequest) -> ActivityProposalMessage:
    model = _build_model()
    messages = [
        ("system", SYSTEM_PROMPT),
        (
            "user",
            "Generate one activity proposal using these preferences:\n{preferences}".format(
                preferences=req.model_dump_json(indent=2),
            ),
        ),
    ]
    try:
        activity_message = model.invoke(messages)
        if not isinstance(activity_message, ActivityProposalMessage):
            activity_message = ActivityProposalMessage.model_validate(activity_message)
        _validate_against_request(activity_message, req)
        return activity_message
    except (ValidationError, ValueError) as ex:
        raise HTTPException(status_code=422, detail=str(ex)) from ex
    except Exception as ex:
        raise HTTPException(status_code=500, detail=str(ex)) from ex
def post_to_main_app(activity_message: ActivityProposalMessage)->Dict[str,Any]:
    url = f"{MAIN_APP_URL}/api/activity-proposals"
    response = requests.post(
        url,
        json= activity_message.model_dump(mode="json"),
        timeout=10
    )
    if response.status_code >=400:
        raise HTTPException(status_code=502, detail={"message": "Main app rejected the generated activity proposal","main_app_status": response.status_code, "main_app_body": response.text,},)
    try:
        return response.json()
    except ValueError:
        return{
            "status_code": response.status_code,
            "body": response.text,
        }
@app.post("/generate-only", response_model=ActivityProposalMessage)
def generate_only(req: GenerateActivityRequest | None = None) -> ActivityProposalMessage:
    if req is None:
        req = GenerateActivityRequest()
    return generate_activity(req)
@app.post("/generate-and-post")
def generate_and_post(req: GenerateActivityRequest | None = None) -> Dict[str, Any]:
    if req is None:
        req = GenerateActivityRequest()
    activity_message = generate_activity(req)
    main_app_response = post_to_main_app(activity_message)
    return {
        "generateActivity": activity_message.model_dump(mode="json"),
        "mainAppresponse":main_app_response,
    }
