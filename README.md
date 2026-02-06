# Argentum-FX

Motor financiero de baja latencia para mercados emergentes. Backend C/C++ y frontend React + TypeScript.

**Status**
- Target actual: Windows (demo funcional).
- Enfoque: pipeline end-to-end con ingesta, bus, persistencia y visualizacion.
- No usar para trading real sin hardening y auditoria completa.

**Highlights**
- Socket manager y datafeed en C.
- Bus interno con protocolo binario.
- LOB y OMS base.
- Persistencia batch (TimescaleDB si hay libpq, CSV fallback).
- Benchmark de latencia y componentes de riesgo iniciales.

**Repo Layout**
- `backend`: motor C/C++ (datafeed, bus, LOB, risk, OMS, backtest).
- `frontend`: Vite + React + TS.
- `infra`: Docker Compose (TimescaleDB, Redis, Kafka opcional).
- `docs`: arquitectura, plan y estrategia git.
- `data`: archivos de demo (ticks JSONL, CSV generado).

**Quick Start (Windows)**
Usa "Developer PowerShell for VS 2022" para tener `cl.exe` y SDKs en PATH.

1. Configurar y compilar:
```powershell
cmake -S . -B build -G "Visual Studio 17 2022"
cmake --build build --config Release
```

2. Ejecutar:
```powershell
.\build\bin\Release\argentum_node.exe
```

Salida esperada:
- Publica ticks desde `data/sample_ticks.jsonl`.
- Persiste en `data/market_ticks.csv` si no hay libpq.

**Frontend**
```powershell
cd frontend
npm install
npm run dev
```

**Docker Infra (opcional)**
```powershell
docker compose -f infra/docker-compose.yml up -d
```

**Datafeed Demo**
El ejecutable reproduce `data/sample_ticks.jsonl`, parsea JSON, normaliza el simbolo y publica por el bus interno.
Luego persiste con `DataWriterService`.

**Configuracion**
- `ARGENTUM_USE_LIBPQ`: habilita COPY batch a TimescaleDB. Requiere libpq en include/lib paths.
- `data/sample_ticks.jsonl`: input de demo.
- `data/market_ticks.csv`: salida local si no hay DB.

**CI**
- `.github/workflows/ci.yml` compila backend y valida formato/lint frontend.

**Formato y Lint**
- `clang-format` para C/C++ (CI en Ubuntu).
- `prettier` y `eslint` en `frontend`.

**Benchmark**
- `backend/include/benchmark/latency_tester.hpp` corre en `main.cpp`.
- Objetivo demo: p99.9 interno <= 1 ms, end-to-end <= 50 ms.

**Docs**
- `docs/ARCHITECTURE.md`
- `docs/GIT_STRATEGY.md`
- `docs/PLAN.md`
- `Doxyfile` para generar docs (Doxygen).

**Disclaimer**
Este repo es tecnico . No es consejo financiero ni un sistema apto para trading real sin certificacion y pruebas exhaustivas.
