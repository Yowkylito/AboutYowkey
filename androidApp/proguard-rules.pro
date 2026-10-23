# R8 rules for the release build. The libraries (Compose, kotlinx.serialization, Koin, Media3, WorkManager,
# Firebase) ship their own consumer rules; nothing in the app is reached by reflection, so nothing more is kept.
