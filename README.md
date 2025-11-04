# SmartAttendance Platform

SmartAttendance is a two-part attendance solution: a React web app that students and staff use in their browsers, and a Spring Boot service that handles authentication, face-recognition workflows, and reporting APIs. The web client only talks to the backend over HTTPS, so each tier can be hosted independently.

Head into `backend/` or `frontend/` for developer setup guides and environment details.
This repo uses a simple two-branch workflow:

| Branch      | Purpose               | Typical Usage                               |
|-------------|-----------------------|---------------------------------------------|
| `main`      | Local development     | Write code, run locally, iterate quickly.   |
| `deploy`    | Cloud deployment      | Build + deploy to cloud environment.        |

> Note: The `deploy` branch is the source of truth for production/cloud deployments.  
> The `main` branch is intended for day-to-day local development.
