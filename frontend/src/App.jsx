import { useEffect, useMemo, useState } from "react";
import agentLogo from "../agentlogo.png";
import { fallbackSimulation } from "./data/fallbackSimulation.js";

const apiUrl = import.meta.env.VITE_SIMULATION_URL ?? "http://localhost:8080/api/simulation";

function App() {
  const [simulation, setSimulation] = useState(fallbackSimulation);
  const [visibleCount, setVisibleCount] = useState(0);
  const [isGenerating, setIsGenerating] = useState(false);
  const [connectionNotice, setConnectionNotice] = useState("");

  useEffect(() => {
    let isMounted = true;

    loadSimulation()
      .then((data) => {
        if (isMounted) {
          setSimulation(data);
          setVisibleCount(0);
          setConnectionNotice("");
        }
      })
      .catch(() => {
        if (isMounted) {
          setSimulation(fallbackSimulation);
          setConnectionNotice("Backend simulation API is unavailable, showing frontend fallback data.");
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const visibleSteps = simulation.steps.slice(0, visibleCount);
  const currentStep = visibleSteps.at(-1);
  const residentStatuses = useMemo(
    () => buildResidentStatuses(simulation.residents, visibleSteps),
    [simulation.residents, visibleSteps]
  );

  const canAdvance = visibleCount < simulation.steps.length;
  const isAtEnd = !canAdvance;
  const buttonLabel = isGenerating ? "Generating" : isAtEnd ? "New Scenario" : "Next Step";

  const handleStepButtonClick = () => {
    if (isGenerating) {
      return;
    }

    if (canAdvance) {
      const nextStep = simulation.steps[visibleCount];
      logSimulationStep(simulation.scenario.id, nextStep, visibleCount + 1).catch(() => {});
      setVisibleCount((count) => count + 1);
      return;
    }

    setIsGenerating(true);
    createNewSimulation()
      .then((data) => {
        setSimulation(data);
        setVisibleCount(0);
        setConnectionNotice("");
      })
      .catch(() => {
        setSimulation(fallbackSimulation);
        setVisibleCount(0);
        setConnectionNotice("Could not create a new scenario from Java, showing frontend fallback data.");
      })
      .finally(() => {
        setIsGenerating(false);
      });
  };

  return (
    <main className="app-shell">
      <section className="process-grid">
        <div className="top-row">
          <ScenarioPanel
            scenario={simulation.scenario}
            currentMessage={currentStepFor("Scenario Agent", currentStep)}
            connectionNotice={connectionNotice}
          />
          <button
            className="next-step-card"
            type="button"
            onClick={handleStepButtonClick}
            disabled={isGenerating}
            aria-label={buttonLabel}
          >
            <span>{buttonLabel}</span>
          </button>
        </div>

        <div className="component-grid">
          <LogsPanel steps={visibleSteps} />
          <KpisPanel kpis={simulation.kpis} />
          <AgentPanel
            title="Social Support Agent"
            className="social-panel"
            currentMessage={currentStepFor("Social Support Agent", currentStep)}
            decisions={decisionsFor("Social Support Agent", visibleSteps, currentStep?.id)}
          />
          <AgentPanel
            title="Health Agent"
            className="health-panel"
            currentMessage={currentStepFor("Health Agent", currentStep)}
            decisions={decisionsFor("Health Agent", visibleSteps, currentStep?.id)}
          />
          <ResourcePanel
            className="resource-panel"
            currentMessage={currentStepFor("Resource Agent", currentStep)}
            resources={resourcesFor(visibleSteps, currentStep?.id)}
            fallbackResources={simulation.scenario.resources}
            decisions={decisionsFor("Resource Agent", visibleSteps, currentStep?.id)}
          />
          <AgentPanel
            title="Activity Agent"
            className="activity-panel"
            currentMessage={currentStepFor("Activity Agent", currentStep)}
            decisions={decisionsFor("Activity Agent", visibleSteps, currentStep?.id)}
          />
          <ResidentsPanel
            residents={simulation.residents}
            statuses={residentStatuses}
            changedResidentIds={Object.keys(currentStep?.residentStatuses ?? {})}
          />
        </div>
      </section>
    </main>
  );
}

async function loadSimulation() {
  const response = await fetch(apiUrl);
  if (!response.ok) {
    throw new Error(`Simulation API returned ${response.status}`);
  }

  return response.json();
}

async function createNewSimulation() {
  const response = await fetch(`${apiUrl}/new`, {
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(`New simulation API returned ${response.status}`);
  }

  return response.json();
}

async function logSimulationStep(scenarioId, step, stepNumber) {
  if (!step) {
    return;
  }

  await fetch(`${apiUrl}/step-log`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      scenarioId,
      stepNumber,
      agent: step.agent,
      log: step.log
    })
  });
}

function Panel({ title, className = "", showIcon = true, children }) {
  return (
    <article className={`panel ${className}`}>
      <div className="panel-title inner-box">
        {showIcon && <img className="agent-icon" src={agentLogo} alt="" aria-hidden="true" />}
        <h2>{title}</h2>
      </div>
      {children}
    </article>
  );
}

function ScenarioPanel({ scenario, currentMessage, connectionNotice }) {
  const roomDetails = [
    { label: "Room", value: scenario.room },
    { label: "Mobility", value: scenario.requiredMobility },
    { label: "Time", value: scenario.time },
    { label: "Places", value: scenario.maxParticipants }
  ];
  const resourceDetails = scenario.resources.map((resource) => ({
    label: "Resource",
    value: `${resource.quantity} x ${resource.name}`
  }));
  const infoDetails = scenario.info.map(splitScenarioInfo);

  return (
    <Panel title="Scenario Agent" className="scenario-panel">
      <div className="scenario-body">
        <div className="scenario-summary">
          <h3>{scenario.title}</h3>
          <div className="scenario-chip-grid">
            {roomDetails.map((detail) => (
              <Detail key={detail.label} label={detail.label} value={detail.value} />
            ))}
          </div>
        </div>
        <div className="scenario-details">
          {connectionNotice && <p className="api-notice">{connectionNotice}</p>}
          {resourceDetails.map((detail) => (
            <Detail key={`${detail.label}-${detail.value}`} label={detail.label} value={detail.value} />
          ))}
          {infoDetails.map((detail) => (
            <Detail
              key={`${detail.label}-${detail.value}`}
              label={detail.label}
              value={detail.value}
              isNote={detail.isNote}
            />
          ))}
        </div>
        <MessageBubble step={currentMessage} />
      </div>
    </Panel>
  );
}

function LogsPanel({ steps }) {
  return (
    <Panel title="Logs" className="logs-panel" showIcon={false}>
      <div className="logs-list inner-box" aria-live="polite">
        {steps.length === 0 ? (
          <p className="muted">No logs yet.</p>
        ) : (
          [...steps].reverse().map((step, index) => (
            <p className="log-line" key={step.id}>
              <span>{String(steps.length - index).padStart(2, "0")}</span>
              {step.log}
            </p>
          ))
        )}
      </div>
    </Panel>
  );
}

function KpisPanel({ kpis }) {
  return (
    <Panel title="KPIs" className="kpis-panel" showIcon={false}>
      <div className="kpi-stack">
        {kpis.map((kpi) => (
          <div className="inner-box kpi-row" key={kpi.label}>
            <span>{kpi.label}</span>
            <strong>{kpi.value}</strong>
          </div>
        ))}
      </div>
    </Panel>
  );
}

function AgentPanel({ title, className = "", currentMessage, decisions }) {
  return (
    <Panel title={title} className={className}>
      <div className="agent-content">
        <MessageBubble step={currentMessage} />
        <DecisionList decisions={decisions} />
      </div>
    </Panel>
  );
}

function ResourcePanel({ className = "", currentMessage, resources, fallbackResources, decisions }) {
  const visibleResources = resources.length > 0
    ? resources
    : fallbackResources.map((resource) => ({ ...resource, status: "Pending", isNew: false }));

  return (
    <Panel title="Resource Agent" className={className}>
      <div className="agent-content">
        <MessageBubble step={currentMessage} />
        <div className="inner-box resource-list">
          {visibleResources.map((resource) => (
            <div
              className={`resource-row ${resource.isNew ? "new-step-item" : ""}`}
              key={`${resource.id}-${resource.status}`}
            >
              <span>{resource.name}</span>
              <strong>{resource.quantity} x {resource.status}</strong>
            </div>
          ))}
        </div>
        <DecisionList decisions={decisions} />
      </div>
    </Panel>
  );
}

function ResidentsPanel({ residents, statuses, changedResidentIds }) {
  return (
    <Panel title="Resident's Agents" className="residents-panel" showIcon={false}>
      <div className="resident-list">
        {residents.map((resident) => (
          <div
            className={`inner-box resident-row ${changedResidentIds.includes(resident.id) ? "new-step-item" : ""}`}
            key={resident.id}
          >
            <div className="resident-name">
              <img className="agent-icon small" src={agentLogo} alt="" aria-hidden="true" />
              <strong>{resident.name}</strong>
            </div>
            <span className={`status-pill ${statusClass(statuses[resident.id])}`}>
              {statuses[resident.id]}
            </span>
          </div>
        ))}
      </div>
    </Panel>
  );
}

function MessageBubble({ step }) {
  if (!step) {
    return null;
  }

  return (
    <div className="inner-box message-bubble new-step-item">
      <div className="message-route">
        <span>{step.from}</span>
        <span>to</span>
        <span>{step.to}</span>
      </div>
      <p>{step.message}</p>
    </div>
  );
}

function DecisionList({ decisions }) {
  if (decisions.length === 0) {
    return null;
  }

  return (
    <div className="decision-stack">
      {decisions.map((decision) => (
        <p
          className={`inner-box decision-item ${decision.isNew ? "new-step-item" : ""}`}
          key={decision.text}
        >
          {decision.text}
        </p>
      ))}
    </div>
  );
}

function Detail({ label, value, isNote = false }) {
  return (
    <div className={`inner-box detail-item ${isNote ? "detail-note" : ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function splitScenarioInfo(item) {
  const separatorIndex = item.indexOf(":");

  if (separatorIndex === -1) {
    return { label: "Info", value: item, isNote: item.length > 55 };
  }

  const label = item.slice(0, separatorIndex).trim();
  const value = item.slice(separatorIndex + 1).trim();

  return { label, value, isNote: value.length > 55 || label.toLowerCase() === "proposed residents" };
}

function currentStepFor(agent, step) {
  if (!step) {
    return undefined;
  }

  const isCurrentAgent = (() => {
    const route = `${step.from} ${step.to}`;

    return step.agent === agent || route.includes(agent);
  })();

  return isCurrentAgent ? step : undefined;
}

function decisionsFor(agent, steps, currentStepId) {
  return steps
    .filter((step) => step.agent === agent && Array.isArray(step.decisions))
    .flatMap((step) => step.decisions.map((decision) => ({
      text: decision,
      isNew: step.id === currentStepId
    })));
}

function resourcesFor(steps, currentStepId) {
  return steps
    .filter((step) => step.agent === "Resource Agent" && Array.isArray(step.resources))
    .flatMap((step) => step.resources.map((resource) => ({
      ...resource,
      isNew: step.id === currentStepId
    })));
}

function buildResidentStatuses(residents, steps) {
  return steps.reduce((statuses, step) => {
    if (!step.residentStatuses) {
      return statuses;
    }

    return { ...statuses, ...step.residentStatuses };
  }, Object.fromEntries(residents.map((resident) => [resident.id, resident.status])));
}

function statusClass(status) {
  return status.toLowerCase().replaceAll(" ", "-");
}

export default App;
