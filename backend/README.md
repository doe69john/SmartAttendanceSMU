# Backend Service (Spring Boot)

The backend is a Spring Boot 3 application that exposes the REST API, orchestrates Supabase integrations, and manages the OpenCV-based training pipeline that produces face-recognition models for the native companion app. Inference runs on the companion, but the service still prepares and distributes the trained artifacts.

> **Branch compatibility:** Windows developers should work from the `main` branch, while macOS users must use `deploy` to avoid platform-specific build issues.
## Architecture Overview

- **Framework**: Spring Boot 3 / Java 21
- **Data & Auth**: Supabase Postgres, Supabase Auth/Storage, Supabase Edge functions
- **Computer Vision**: OpenCV (Haar cascade, LBPH recognizer) via the shared `companion/` module and backend trainers
- **Messaging**: Server-Sent Events for live session updates
- **Storage**: Supabase buckets for face captures, LBPH model artifacts, and companion installers
- **Configuration**: `.env.local` (for local dev) or environment variables in production

Key modules:

- `backend/service` - the primary Spring application (controllers, services, repositories)
- `backend/companion` - reusable OpenCV utilities used by both the service and the native companion app
- `backend/service/runtime` - filesystem workspace for cached models, logs, and temporary face datasets

## Environment Configuration

Copy `backend/.env.local` (or create it) and fill it with your Supabase credentials and optional passcodes. The application already imports this file, so you only need to export a profile before launching.

Key environment options:
  - `SUPABASE_*` variables for Postgres, Auth, Storage, and JWT settings.
  - `SUPABASE_STORAGE_SERVICE_JWT` (or `SUPABASE_SERVICE_ROLE_KEY`) for background storage uploads when no user session is available.
  - `PROFESSOR_PASSCODE` or `PROFESSOR_PASSCODE_HASH` to allow fallback instructor access.
  - `ADMIN_PASSCODE` or `ADMIN_PASSCODE_HASH` to protect admin onboarding with a shared secret.
  - `COMPANION_AUTO_PUBLISH`: set to "true" to build companion installers on startup (defaults to `false`).
  - `SUPABASE_RESET_REDIRECT_URL`: absolute URL for Supabase password recovery links (e.g. `https://app.example.com/auth/reset/confirm`). Ensure Supabase's **Site URL** points to the root of your web app (e.g. `https://app.example.com`) so recovery emails redirect back with `?type=recovery&access_token=...`. Add the same `/auth/reset/confirm` URL to Supabase Auth → **Additional Redirect URLs**; otherwise Supabase will ignore the custom redirect and fall back to the Site URL.

- **PowerShell**
  ```powershell
  Set-Location backend/service
  $env:SPRING_PROFILES_ACTIVE = "dev"
  ..\mvnw.cmd clean package
  ..\mvnw.cmd spring-boot:run
  ```

- **Command Prompt**(Recommended for windows)
  ```cmd
  cd backend\service
  set SPRING_PROFILES_ACTIVE=dev
  ..\mvnw.cmd spring-boot:run
  ```

- **macOS / Linux (bash/zsh)**
  ```bash
  cd backend/service
  export SPRING_PROFILES_ACTIVE=dev
  chmod 777 ../mvnw
  ../mvnw spring-boot:run
  ```
  please make sure last line of build output is `Face image storage: Supabase bucket 'face-images' via https://*.supabase.co/storage/v1  ` (means server is running as intended head to `/frontend` to start up the frontend using another shell.)

  if buid output says "BUILD SUCCESS" build actually failed and service did not start up.


> For production, inject the same key/value pairs as environment variables. If you keep them in a file, provide it via `SPRING_CONFIG_ADDITIONAL_LOCATION`.

## Common Commands

| Task | Command |
|------|---------|
| Run in dev mode | `../mvnw spring-boot:run` |
| Run tests | `../mvnw test` |
| Build JAR | `../mvnw package` |

## Companion & Asset Delivery

- `/api/companion/releases/...` exposes installer metadata/downloads
- `/api/companion/assets/cascade` streams the Haar cascade bundled with the project
- `/api/companion/sections/{id}/models/...` streams LBPH model and label files (downloaded on demand from Supabase Storage)

These endpoints let the native companion app obtain everything it needs without the React client making direct Supabase calls.

## Deployment Notes

- Set `SPRING_PROFILES_ACTIVE` appropriately (`dev`, `prod`, etc.).
- Ensure Supabase anon credentials and database connectivity are available.
- Mount or persist `backend/service/runtime/` if you need cached recognizer state across restarts.
- Behind a gateway, expose `/api/**` and (optionally) `/api/companion/**`.
