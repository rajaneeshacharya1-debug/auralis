# Auralis Protect Android Structure

This Android app uses a clean layered structure:

- core: constants, permissions, security, utilities
- design: theme and reusable UI components
- domain: models, repository interfaces, use cases
- data: Android implementations for battery, audio, location, SMS, network
- service: foreground/background service classes
- receiver: Android broadcast receivers
- feature: user-facing screens
- navigation: Compose navigation
- di: dependency injection setup

Do not place all code directly in MainActivity.kt. MainActivity should only start the app UI.
