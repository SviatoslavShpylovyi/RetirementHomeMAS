### 1 Health check

```http
GET /api/health
```

Example:

```bash
curl http://localhost:8080/api/health
```

Response:

```json
{
  "status": "ok"
}
```

Use this endpoint to check whether the Java backend is running.

---

### 2 Submit an activity proposal

```http
POST /api/activity-proposals
```

This endpoint accepts an activity proposal JSON. It does not create the final event directly. It queues the JSON and forwards it to `ScenarioAgent` through JADE.

Expected request body:

```json
{
  "eventType": "CREATE_ACTIVITY_PROPOSAL",
  "source": "langchain-activity-generator",
  "payload": {
    "id": "LC-ACT-001",
    "name": "Afternoon Music Session",
    "type": "MUSIC",
    "maxParticipants": 8,
    "requiredMobilityLevel": "LOW"
  },
  "explanation": "A music activity is suitable for residents with low mobility and matches known preferences."
}
```

Success response:

```json
{
  "status": "accepted"
}
```

Status codes:

| Status | Meaning |
|---:|---|
| `202` | JSON accepted and queued for `ScenarioAgent` |
| `400` | Empty JSON body |
| `405` | Wrong HTTP method |

Supported `ActivityType` values:

```text
MUSIC
ART
BOARD_GAMES
WALKING
READING
FITNESS
MOVIE
SOCIAL_TEA
KNITTING
CROCHETING
POKER
BINGO
```

Supported `MobilityLevel` values:

```text
LOW
MEDIUM
HIGH
```

### 3 Logs
```http
GET http://localhost:8080/api/logs
```
Output:
```text
[
    {
        "id": 1,
        "timestamp": "2026-06-05T17:52:55.465728400Z",
        "level": "INFO",
        "source": "social-support-agent",
        "action": "AGENT_STARTED",
        "message": "SocialSupportAgent started and is ready to receive frontend log events",
        "details": {}
    },
    {
        "id": 2,
        "timestamp": "2026-06-05T17:52:55.573268800Z",
        "level": "INFO",
        "source": "activity-agent",
        "action": "RESIDENT_ACCEPTED_PROPOSAL",
        "message": "Resident R1 accepted proposal E1",
        "details": {
            "finalStatus": "CONFIRMED",
            "answer": "ACCEPTED",
            "residentId": "R1",
            "proposalId": "E1"
        }
    },
    {
        "id": 3,
        "timestamp": "2026-06-05T17:52:55.573268800Z",
        "level": "INFO",
        "source": "activity-agent",
        "action": "RESIDENT_DECLINED_PROPOSAL",
        "message": "Resident R2 declined proposal E1: Resident is not interested in this activity",
        "details": {
            "reason": "Resident is not interested in this activity",
            "answer": "DECLINED",
            "finalStatus": "DECLINED",
            "residentId": "R2",
            "proposalId": "E1"
        }
    },
    {
        "id": 4,
        "timestamp": "2026-06-05T17:52:55.574779700Z",
        "level": "INFO",
        "source": "social-support-agent",
        "action": "RESIDENT_FINAL_ACCEPTED_RECORDED",
        "message": "Resident R1 was recorded as accepted for booked proposal E1",
        "details": {
            "eventWasBooked": true,
            "activityType": "MUSIC",
            "participationCount": 1,
            "residentId": "R1",
            "status": "CONFIRMED",
            "proposalId": "E1",
            "activityId": "A1"
        }
    },
    {
        "id": 5,
        "timestamp": "2026-06-05T17:52:55.576789700Z",
        "level": "INFO",
        "source": "social-support-agent",
        "action": "RESIDENT_FINAL_DECLINED_RECORDED",
        "message": "Resident R2 was recorded as declined for proposal E1",
        "details": {
            "eventWasBooked": true,
            "activityType": "MUSIC",
            "residentId": "R2",
            "declinedCount": 1,
            "status": "DECLINED",
            "proposalId": "E1",
            "activityId": "A1"
        }
    },
    {
        "id": 6,
        "timestamp": "2026-06-05T17:52:55.579793100Z",
        "level": "INFO",
        "source": "activity-agent",
        "action": "EVENT_BOOKED",
        "message": "Proposal E1 was booked successfully",
        "details": {
            "confirmedResidents": 1,
            "proposalId": "E1",
            "outcome": "BOOKED"
        }
    }
]
```