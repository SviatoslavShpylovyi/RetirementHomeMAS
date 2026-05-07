from __future__ import annotations
from datetime import datetime
from enum import Enum
from typing import  List, Literal,Dict,Any
from pydantic import BaseModel, Field, field_validator, model_validator 

MAXPARTICIPANTS = 30
class ActivityType(str,Enum):
    MUSIC = "MUSIC"
    ART = "ART"
    BOARD_GAMES = "BOARD_GAMES"
    WALKING = "WALKING"
    READING = "READING"
    FITNESS = "FITNESS"
    MOVIE = "MOVIE"
    SOCIAL_TEA = "SOCIAL_TEA"
    KNITTING = "KNITTING"
    CROCHETING = "CROCHETING"
    POKER = "POKER"
    BINGO = "BINGO"
class MobilityLevel(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"
class TimeSlotModel(BaseModel):
    startTime: datetime = Field(description = "Event start time as ISO-8601 local date-time.")
    endTime: datetime = Field(description="Event end time as ISO-8601 local date-time.")
    @classmethod
    @model_validator(mode="after")
    def validate_time(self):
        if self.startTime>=self.endTime:
            raise ValueError("Event cannot start after or at the same time as it ends")
        return self
class ActivityModel(BaseModel):
    id: str = Field(description="Unique activity identifier. Example: LC-ACT-001.")
    name: str = Field(description="Human-readable name of the activity")
    type: ActivityType = Field(description="Type/category of the proposed activity")
    maxParticipants: int = Field(
        gt = 0,
        description="Maximum number of residents who can participate in this activity"
    )
    requiredMobilityLevel: MobilityLevel = Field(
        description="Minimum mobility level required for this activity"
    )
    @field_validator("id","name")
    @classmethod
    def validate_not_empty(cls, value: str)->str:
        if not value or not value.strip():
            raise ValueError("Field canot be empty")
        return value.strip()
    @field_validator("maxParticipants")
    @classmethod
    def validate_num(cls, value:int)->int:
        if value > MAXPARTICIPANTS:
            raise ValueError("MaxNumber of participants is too big")
        if value <4:
            raise ValueError("MaxNumber of participants is too small")
        return value
class ActivityProposalMessage(BaseModel):
    eventType: Literal["CREATE_ACTIVITY_PROPOSAL"] = Field(
        default="CREATE_ACTIVITY_PROPOSAL",
        description="Type of JSON message sent to the JADE ScenarioAgent."
    )
    source: Literal["langchain-activity-generator"] = Field(
        default="langchain-activity-generator",
        description="Name of the external system that generated the activity."
    )
    payload: ActivityModel = Field(
        description="Generated activity. ScenarioAgent will then transform it into the EventRequest"
    )
    explanation: str = Field(
        description="Brief 2 sentence max expanation of why this activity was generated"
    )
class GenerateActivityRequest(BaseModel):
    preferredEvents: list[ActivityType] = Field(
        default_factory=list,
        description="Preferred activity types. Empty list means no restriction."
    )

    preferredNumOfParticipants: list[int] = Field(
        default_factory=list,
        description=(
            "Preferred participant numbers. Empty list means no restriction. "
            "If two numbers are provided, they are interpreted as min and max."
        )
    )

    preferredMobility: list[MobilityLevel] = Field(
        default_factory=list,
        description="Preferred required mobility levels. Empty list means no restriction."
    )