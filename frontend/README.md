# Frontend Client (React + Vite)

The frontend is a Vite-powered React SPA for students and staff. It consumes the REST API exposed by the backend and never reaches into Supabase directly.

## Architecture Overview

- **Framework**: React 18 + Vite
- **Language**: TypeScript
- **State/Data**: React Query, custom hooks
- **UI Toolkit**: shadcn/ui, Tailwind CSS
- **API Layer**: Generated OpenAPI client (`src/lib/api.ts`, `src/lib/openapi-client.ts`)

Key directories:

- `src/components/` reusable UI and feature components (face capture, live session dashboards, etc.)
- `src/pages/` route-level screens
- `src/lib/` API client, auth helpers, domain utilities
- `scripts/` tooling (OpenAPI generator)

## Environment Configuration

`frontend/.env.local` holds the client-only values:

```
VITE_API_BASE_URL=http://localhost:18080/api
VITE_COMPANION_BRIDGE_URL=http://127.0.0.1:4455
```

Set `VITE_API_BASE_URL` to the public URL of your backend when deploying (for example, `https://attendance-api.example.com/api`). The companion bridge URL points to the native desktop application that handles face recognition workflows—browser clients always enforce the companion guard, so no additional flags are required.

## Installing & Running

- **Windows (PowerShell)**
  ```powershell
  Set-Location frontend
  npm install
  npm run dev
  ```

- **macOS / Linux (bash/zsh)**
  ```bash
  cd frontend
  npm install
  npm run dev
  ```

- **Windows (Command Prompt)**
  ```cmd
  cd frontend
  npm install
  npm run dev
  ```

The dev server listens on `http://localhost:5173` by default.

## Common Scripts

| Task | Command |
|------|---------|
| Start dev server | `npm run dev` |
| Type-check & lint | `npm run lint` |
| Create production build | `npm run build` |
| Preview production build | `npm run preview` |
| Regenerate OpenAPI types | `npm run generate:api` (backend must be running) |

## Deploying

1. Set `VITE_API_BASE_URL` (and other vars if needed) in your hosting environment.
2. Run `npm run build` to produce the static site in `dist/`.
3. Deploy the `dist/` folder to your CDN/static host of choice.

## Companion Integration

Live sessions can optionally coordinate with the native companion app. The SPA now receives signed download links for LBPH models, labels, and cascade files from the backend and relays them to the companion bridge at `VITE_COMPANION_BRIDGE_URL`.

