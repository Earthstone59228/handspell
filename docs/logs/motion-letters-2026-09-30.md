# J and Z integration — 2026-09-30

The committed motion recognizer now receives raw camera landmarks for J/Z drills. Target changes, camera restarts and stops clear its buffered path. Completed paths use the existing match and progress flows; static letters retain the single-frame classifier and hold confirmation.

The alphabet page offers all 26 drills. J/Z show experimental recognition notices, starting handshape guides and instructions to trace and pause. Menu, settings and progress counts include motion letters. Static story and speed packs and personal handshape calibration retain their existing exclusions.

Validation: frontend production build; Android debug build; 217 unit tests with zero failures; lint passed. APK installed successfully on connected device R5GL14MCNJN and MainActivity launched with a running process. Recognition thresholds remain supported by synthetic tests only; perform J/Z with both hands on hardware before claiming accuracy. The inherited random-motion test is not a measured real-world false-accept rate.
