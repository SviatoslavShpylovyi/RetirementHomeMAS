export const fallbackSimulation = {
  kpis: [
    { label: "number of events accepted", value: "1" },
    { label: "number of proposed events", value: "2" },
    { label: "average number of attended events per resident", value: "0.40" }
  ],
  scenario: {
    id: "E1",
    title: "Morning Music Session",
    type: "MUSIC",
    room: "Common Room",
    time: "2026-06-12 10:00-11:00",
    requiredMobility: "LOW",
    maxParticipants: 10,
    resources: [{ id: "RES-SPEAKER", name: "Portable Speaker", quantity: 1 }],
    info: [
      "Room capacity: 15",
      "Accessible room: yes",
      "Proposed residents: Anna, Jan, Maria, Ewa, Piotr, Zofia, Tomasz"
    ]
  },
  residents: [
    { id: "R1", name: "Anna", status: "Waiting" },
    { id: "R2", name: "Jan", status: "Waiting" },
    { id: "R3", name: "Maria", status: "Waiting" },
    { id: "R4", name: "Ewa", status: "Waiting" },
    { id: "R5", name: "Piotr", status: "Waiting" },
    { id: "R6", name: "Zofia", status: "Waiting" },
    { id: "R7", name: "Tomasz", status: "Waiting" }
  ],
  steps: [
    {
      id: "scenario-received",
      agent: "Scenario Agent",
      kind: "message",
      from: "api-gateway-agent",
      to: "Scenario Agent",
      message: "Received activity proposal JSON and parsed Morning Music Session.",
      log: "ScenarioAgent received JSON and parsed activity E1: Morning Music Session"
    },
    {
      id: "scenario-social-request",
      agent: "Scenario Agent",
      kind: "message",
      from: "Scenario Agent",
      to: "Social Support Agent",
      message: "Ask for resident recommendations for Morning Music Session.",
      log: "scenario-agent -> social-support-agent: recommendation request for proposal E1"
    },
    {
      id: "social-support-decision",
      agent: "Social Support Agent",
      kind: "decision",
      from: "Social Support Agent",
      to: "Scenario Agent",
      message: "Recommendation decision sent back to Scenario Agent.",
      log: "social-support-agent -> scenario-agent: Anna, Jan and Ewa recommended; others excluded by preference, willingness or noise",
      decisions: [
        "Anna recommended: likes music and is willing to participate.",
        "Jan recommended: open to suggestions and has recent low attendance.",
        "Maria not recommended: not willing and sensitive to noisy activities.",
        "Ewa recommended: low recent attendance and seated room support.",
        "Piotr not recommended: activity is not a strong preference match.",
        "Zofia not recommended: avoids loud music.",
        "Tomasz not recommended: not willing and prefers small groups."
      ],
      residentStatuses: {
        R1: "Recommended",
        R2: "Recommended",
        R3: "Not recommended",
        R4: "Recommended",
        R5: "Not recommended",
        R6: "Not recommended",
        R7: "Not recommended"
      }
    },
    {
      id: "activity-health-request",
      agent: "Activity Agent",
      kind: "message",
      from: "Activity Agent",
      to: "Health Agent",
      message: "Health check for proposal E1 with residents R1, R2, R4.",
      log: "activity-agent -> health-agent: health check for proposal E1"
    },
    {
      id: "health-decision",
      agent: "Health Agent",
      kind: "decision",
      from: "Health Agent",
      to: "Activity Agent",
      message: "Health answer for proposal E1.",
      log: "health-agent -> activity-agent: E1|R1:SAFE|R2:SAFE|R4:SAFE",
      decisions: [
        "Anna safe: LOW mobility matches the activity requirement.",
        "Jan safe: MEDIUM mobility exceeds the activity requirement.",
        "Ewa safe: LOW mobility matches the activity requirement and room is accessible."
      ],
      residentStatuses: {
        R1: "Safe",
        R2: "Safe",
        R3: "Not recommended",
        R4: "Safe",
        R5: "Not recommended",
        R6: "Not recommended",
        R7: "Not recommended"
      }
    },
    {
      id: "activity-resource-request",
      agent: "Activity Agent",
      kind: "message",
      from: "Activity Agent",
      to: "Resource Agent",
      message: "Booking request for Common Room and one Portable Speaker.",
      log: "activity-agent -> resource-agent: booking request for proposal E1",
      decisions: [
        "Active participants after health check: 3.",
        "Resource booking requested for 2026-06-12 10:00-11:00."
      ]
    },
    {
      id: "resource-decision",
      agent: "Resource Agent",
      kind: "decision",
      from: "Resource Agent",
      to: "Activity Agent",
      message: "Proposal E1 accepted and resources booked.",
      log: "resource-agent -> activity-agent: E1|ACCEPTED",
      resources: [
        { id: "ROOM1", name: "Common Room", quantity: 1, status: "Booked" },
        { id: "RES-SPEAKER", name: "Portable Speaker", quantity: 1, status: "Booked" }
      ],
      decisions: [
        "Common Room is available and has capacity for three active participants.",
        "Portable Speaker quantity 1 is available and marked as used."
      ]
    },
    {
      id: "activity-accepted",
      agent: "Activity Agent",
      kind: "decision",
      from: "Activity Agent",
      to: "Scenario Agent",
      message: "Proposal E1 accepted after health and resource checks.",
      log: "Proposal E1 was booked successfully.",
      decisions: [
        "Morning Music Session accepted.",
        "Anna, Jan and Ewa remain active participants.",
        "Other residents remain declined or not recommended for this event."
      ],
      residentStatuses: {
        R1: "Scheduled",
        R2: "Scheduled",
        R3: "Declined",
        R4: "Scheduled",
        R5: "Declined",
        R6: "Declined",
        R7: "Declined"
      }
    }
  ]
};
