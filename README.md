# Retirement Home MAS

Multi-agent retirement home event simulation with a Java JADE backend, a Python LLM activity generator, and a React frontend dashboard.

## What It Does

- Generates activity scenarios for retirement home residents.
- Simulates agent communication step by step in the frontend.
- Shows Scenario, Social Support, Resource, Health, Activity, Resident Agents, Logs, and KPIs.
- Uses the Python LLM service when available.
- Falls back to a local Java scenario generator when the LLM service is not running.

## Requirements

- Java 21
- Maven
- Node.js and npm
- Python 3.12 or compatible
- OpenAI API key for LLM-generated scenarios

## Setup

Install frontend dependencies:

```powershell
cd frontend
npm.cmd install
```

Install Python LLM dependencies:

```powershell
cd ..\LangChainLLMAgent
py -m pip install -r requirements.txt
```

Create `LangChainLLMAgent/.env` from `LangChainLLMAgent/.env-example`:

```env
OPENAI_API_KEY=your-key
OPENAI_MODEL=gpt-5.4-mini
MAIN_APP_URL=http://localhost:8080
```

Do not commit `.env`; it is ignored by git.

## Run

Start the Python LLM agent:

```powershell
cd LangChainLLMAgent
py -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Start the Java backend from IntelliJ using:

```text
org.example.Engine
```

Or from terminal:

```powershell
mvn exec:java -Dexec.mainClass="org.example.Engine"
```

Start the React frontend:

```powershell
cd frontend
npm.cmd run dev -- --port 5173
```

Open:

```text
http://127.0.0.1:5173/
```

## Useful URLs

- Frontend: `http://127.0.0.1:5173/`
- Java simulation API: `http://localhost:8080/api/simulation`
- Python LLM API docs: `http://127.0.0.1:8000/docs`

## How To Use

1. Open the frontend.
2. Click `Next Step` to reveal the next agent communication or decision.
3. When the scenario is complete, the button changes to `New Scenario`.
4. Click `New Scenario` to request a new generated scenario from the backend.

## Notes

- If the LLM service is not running, Java logs a fallback message and creates a local scenario.
- Frontend logs are newest-first and scroll inside the Logs panel.
- Java console receives frontend step logs through `/api/simulation/step-log`.
- Scenario dates are generated in the future.
- KPIs update as scenarios are proposed and accepted.

## Verification

Backend:

```powershell
mvn test
```

Frontend:

```powershell
cd frontend
npm.cmd run build
```
