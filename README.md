# VPE Hyundai — Vehicle Persona Engine

Native Android (Kotlin) prototype for a vehicle personality AI.

## Current features
- Deterministic, bounded weighted vehicle-to-personality mapping
- Five long-term personality dimensions and two short-term mood dimensions
- Adjustable age, odometer, fuel, maintenance, exploration, and long-distance driving
- Korean example dialogue with personality-sensitive Android TextToSpeech pitch/rate
- `PersonalityMemory` exponential moving average utility for future persistent updates

## Run
1. Open repository root in Android Studio (JDK 17).
2. Sync Gradle, install Android SDK 35.
3. Run the `app` configuration on an Android 9+ emulator or device.
4. Install a Korean-capable Android TTS voice to hear the dialogue.

## Architecture / limitations
`PersonaEngine.calculate(snapshot)` is pure and deterministic. Sliders currently provide simulated vehicle inputs; **Pleos Vehicle SDK is not integrated or tested**. No real vehicle data is accessed. The EMA memory utility is provided but not yet connected to persistent storage. There is no LLM integration or free-form speech recognition; dialogue is rule-based. Android TTS supports pitch and speed controls, not full emotional voice synthesis. Weight coefficients are initial design hypotheses and require calibration and scenario testing. Maintenance score and route statistics need future data-source adapters; do not treat them as confirmed Vehicle SDK fields.

## Next
- Add a `VehicleDataSource` interface with demo and Pleos SDK adapters
- Persist long-term traits and timestamped maintenance events using Room
- Apply stable long-term traits to voice generation, and temporary mood as modulation
- Add scenario/unit tests for reproducibility, missing data, and threshold transitions
- Connect LLM dialogue with persona state and a voice engine supporting expressive synthesis
