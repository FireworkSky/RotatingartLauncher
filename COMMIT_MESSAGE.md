Fix text control empty text persistence and improve control editor

Major Changes:
- Fix text control empty text persistence: Added ControlDataDeserializer to ensure displayText is correctly deserialized even for empty strings. Fixed ControlDataSyncManager to not use source.name as default when displayText is empty.
- Unify JSON configuration: Created ControlDataSerializer for conditional field serialization. Updated ControlConfig load methods to use serializeNulls() and custom deserializer.
- Refactor control editor: Split duplicate code into manager classes (ControlEditDialogSeekBarManager, ControlEditDialogVisibilityManager, etc.). Fixed SeekBar listener accumulation issue.
- UI improvements (MD3): Converted top toolbar buttons to draggable MD3 floating controls. Created UnifiedEditorSettingsDialog and ControlLayoutSettingsDialog. Fixed touch event issues.
- Enable hardware acceleration: Enabled for GameActivity and ControlLayout to fix RippleDrawable animation errors.
- Add text control type: Support text content editing, shape selection, rotation, and appearance settings.
- Add control rotation: Support rotation angle adjustment in edit dialog.
- Add control selected state: Visual feedback for selected controls.
- Layout management: Fixed gamepad default layout export failure. Added layout import functionality.
- Code cleanup: Removed deprecated dialogs and unused resources.
- Bug fixes: Fixed opacity persistence, JSON export field issues, NullPointerException, and various click response issues.

Statistics: 97 files changed, +5487 insertions, -6882 deletions
