package com.app.ralaunch.locales

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

object EnString : LocaleStrings {
    override val confirm: String = "Confirm"
    override val cancel: String = "Cancel"

    override val appName = "Rotating art Launcher" // Typically kept as-is for app names unless officially translated
    override val appDescription = "Game Launcher"
    override val appAuthors = "Authors:" // Or "Author:" if singular is preferred and consistent

    override val navrailGame: String = "Games"
    override val navrailFile: String = "Files"
    override val navrailDownload: String = "Downloads"
    override val navrailSettings: String = "Settings"
    override val navrailSettingsGeneral: String = "General"
    override val navrailSettingsControl: String = "Controls"
    override val navrailSettingsAdvanced: String = "Advanced"
    override val navrailSettingsAbout: String = "About"

    override val settingsLanguage: String = "Language"
    override val settingsFollowSystem: String = "Follow System"
    override val settingsTheme: String = "Theme"
    override val settingsDark: String = "Dark"
    override val settingsLight: String = "Light"
    override val settingsDynamicColor: String = "Dynamic Color"
    override val settingsThemeColor: String = "Theme Color"
    override val settingsThemeColorDescription: String = "Choose the theme color for the application"
    override val settingsPalette: String = "Palette"
    override val settingsCustom: String = "Custom"
    override val settingsRenderer: String = "Renderer"
    override val settingsAuto: String = "Auto"
    override val settingsVirtualJoystickOpacity: String = "Virtual Joystick Opacity"
    override val settingsVibrationEnabled: String = "Haptic Feedback"
    override val settingsServerGc: String = "Server GC"
    override val settingsServerGcDescription: String = "Multi-core optimization, higher throughput, higher memory usage"
    override val settingsConcurrentGc: String = "Concurrent GC"
    override val settingsConcurrentGcDescription: String = "Reduces pause times"
    override val settingsTieredCompilation: String = "Tiered Compilation"
    override val settingsTieredCompilationDescription: String = "Speeds up startup time"
    override val settingsCoreClrDebugLog: String = "CoreCLR Debug Log"
    override val settingsThreadAffinity: String = "Thread Affinity"
    override val settingsThreadAffinityDescription: String = "Bind main thread to big cores for improved performance"

    override val fileManagerCreate: String = "Create"
    override val fileManagerCreateFile: String = "New File"
    override val fileManagerCreateFolder: String = "New Folder"
    override val fileManagerInputDialogTitle: String = "Create New Item"
    override val fileManagerInputFileNameLabel: String = "Name"
    override val fileManagerInputPlaceholder: String = "Enter file or folder name"
    override val fileManagerCurrentLocation: String = "Location:"
    override val fileManagerPathDialogLabel: String = "Enter Path"
    override val fileManagerNavigateUp: String = "Go Up"
    override val fileManagerOpenPath: String = "Navigate to Path"
    override val fileManagerFolder: String = "Folder"
    override val fileManagerFile: String = "File"
    override val fileManagerMoreActions: String = "More Actions"
    override val fileManagerOpenAction: String = "Open"
    override val fileManagerOperationDialogTitle: String = "File Operation"
    override val fileManagerOperationsTitle: String = "Available Actions"
    override val fileManagerOpenButton: String = "Open"
    override val fileManagerCopyButton: String = "Copy"
    override val fileManagerMoveButton: String = "Move"
    override val fileManagerRenameButton: String = "Rename"
    override val fileManagerDeleteButton: String = "Delete"
    override val fileManagerOperationButtonOpenDesc: String = "Open"
    override val fileManagerOperationButtonCopyDesc: String = "Copy"
    override val fileManagerOperationButtonMoveDesc: String = "Move"
    override val fileManagerOperationButtonRenameDesc: String = "Rename"
    override val fileManagerOperationButtonDeleteDesc: String = "Delete"
    override val fileManagerDeleteConfirmTitle: String = "Confirm Deletion"
    override val fileManagerDeleteConfirmMessage: String = "Are you sure you want to delete \"%s\"? This action cannot be undone."
    override val fileManagerDeleteAction: String = "Delete"
    override val fileManagerRenameDialogTitle: String = "Rename"
    override val fileManagerRenameInputLabel: String = "New Name"
    override val fileManagerRenameAction: String = "OK" // Confirm is also common
    override val fileManagerOperationConfirmTitleTemplate: String = "Confirm %s"
    override val fileManagerOperationConfirmMessageTemplate: String =
        "Are you sure you want to %s \"%s\" to \"%s\"?"
    override val fileManagerOperationCopy: String = "copy"
    override val fileManagerOperationMove: String = "move"
    override val fileManagerOperationCopyAction: String = "Copy"
    override val fileManagerOperationMoveAction: String = "Move"
    override val fileManagerSnackbarCreatedFile: String = "File created successfully"
    override val fileManagerSnackbarFileExistsOrFailed: String = "File already exists or creation failed"
    override val fileManagerSnackbarCreatedFolder: String = "Folder created successfully"
    override val fileManagerSnackbarFolderExistsOrFailed: String = "Folder already exists or creation failed"
    override val fileManagerSnackbarCreateFailedTemplate: String = "Creation failed: %s"
    override val fileManagerSnackbarCopiedTemplate: String = "Selected for copy: %s"
    override val fileManagerSnackbarMovedTemplate: String = "Selected for move: %s"
    override val fileManagerSnackbarDeleted: String = "Deleted successfully"
    override val fileManagerSnackbarDeleteFailed: String = "Deletion failed"
    override val fileManagerSnackbarRenamed: String = "Renamed successfully"
    override val fileManagerSnackbarRenameFailed: String = "Rename failed"
    override val fileManagerSnackbarOperationSuccess: String = "Operation successful"
    override val fileManagerSnackbarOperationFailedTemplate: String = "Operation failed: %s"
    override val fileManagerSnackbarOpeningTemplate: String = "Opening: %s"
    override val fileManagerSnackbarNoAppToOpenTemplate: String = "No app available to open this file type: %s"
    override val fileManagerSnackbarOpenFailedTemplate: String = "Failed to open file: %s"
    override val fileManagerSnackbarFileNotFoundTemplate: String = "File not found: %s"

    override val aboutIntroduction: String = "Introduction"
    override val aboutIntroductionText: String =
        "Rotating Art Launcher is a game launcher designed specifically for the Android platform, capable of running .NET games developed using the FNA/XNA framework. This project achieves native execution of Windows PC games on Android devices by integrating the .NET Core Runtime and SDL2."
    override val aboutFeature: String = "Features"
    override val aboutFeatureDotNetRuntime: String = ".NET 8 Runtime"
    override val aboutFeatureFNAXNA: String = "FNA/XNA Compatibility"
    override val aboutFeatureMaterialYou: String = "Material You Dynamic Theming"
    override val aboutFeatureFullscreen: String = "Fullscreen & Notch Support"
    override val aboutSupportedGamesTitle: String = "Supported Games"
    override val aboutSupportedGameOtherFna: String = "Other FNA Games"
    override val aboutSystemRequirementsTitle: String = "System Requirements"
    override val aboutSystemRequirementStorage: String = "500MB+ Storage"
    override val aboutTechStackTitle: String = "Technology Stack"
    override val aboutKnownIssuesTitle: String = "Known Issues"
    // Consider using \n for line breaks in plain strings if AnnotatedString isn't strictly needed here
    override val aboutKnownIssueContext: AnnotatedString = buildAnnotatedString {
        append("• Some games might require additional library files\n")
        append("• Performance may be limited on low-end devices\n")
        append("• Certain game mods might be incompatible")
    }
    override val aboutContributeTitle: String = "Contribution"
    override val aboutContributeContext: AnnotatedString = buildAnnotatedString {
        append("Contributions via Issues and Pull Requests are welcome!\n")
        append("1. Fork the repository\n")
        append("2. Create your feature branch\n")
        append("3. Commit your changes\n")
        append("4. Push to the branch\n")
        append("5. Open a Pull Request")
    }
    override val aboutChangelogTitle: String = "Changelog - %s (%s)" // Placeholders remain
    override val aboutChangelogContext: AnnotatedString = buildAnnotatedString {
        append("• ✨ Initial release\n")
        append("• 🎮 Support for tModLoader and FNA games\n")
        append("• 🖥️ Fullscreen and notch display support\n")
        append("• 📦 Automatic resource extraction\n")
        append("• 🌐 Bilingual support (Chinese/English)")
    }
    override val aboutLicenseTitle: String = "License"
    override val aboutLicenseContext: AnnotatedString = buildAnnotatedString {
        append("This project is open-sourced under the GNU Lesser General Public License v3.0 (LGPLv3).\n")
        append("\nThird-party Licenses:\n")
        append("• SDL2 - Zlib License\n")
        append("• GL4ES - MIT License\n")
        append("• .NET Runtime - MIT License\n")
        append("• FNA - Ms-PL License")
    }
    override val aboutCreditsThanksTitle: String = "Acknowledgements"
    override val aboutCreditsThanksContext: AnnotatedString = buildAnnotatedString {
        append("Thanks to all the open-source projects and contributors:\n")
        append("• SDL Project\n")
        append("• GL4ES\n")
        append("• .NET Runtime\n")
        append("• FNA\n")
        append("• And all contributors and users")
    }
    override val aboutAuthorsTitle: String = "Authors" // Often redundant if appAuthors covers it, but kept as requested
    override val aboutContactTitle: String = "Contact"
    override val aboutContactInstructions: AnnotatedString = buildAnnotatedString {
        appendLine("For questions or suggestions, please:")
        appendLine("• Submit an Issue")
        appendLine("• Visit Discussions")
    }
    override val aboutContactIssueButton: String = "Submit Issue"

    override val setupOneStepTitle: String = "Complete Initial Setup in One Step"
    override val setupOneStepDescription: AnnotatedString = buildAnnotatedString {
        appendLine("• Agree to legal terms to start installation")
        appendLine("• Includes built-in .NET runtime, no manual download needed")
        appendLine("• Real-time display of installation progress and status")
    }
    override val setupOptimizedForLandscape: String = "Initialization experience optimized for landscape devices"
    override val setupLegalTitle: String = "Legal Notice"
    override val setupLegalContent: AnnotatedString = buildAnnotatedString {
        appendLine("This launcher is a third-party tool and requires the original game files to run. You must own a legally authorized copy of the game to use this software.\n")
        appendLine("The developers of this launcher are not affiliated with the original game creators and are not responsible for any legal consequences arising from the use of this software.\n")
        appendLine("Using this software indicates that you have read, understood, and agree to comply with the above terms.")
    }
    override val setupComponentsListTitle: String = "Component List"
    override val setupInstallButtonStart: String = "Start Installation"
    override val setupInstallButtonInstalling: String = "Installing..."
    override val setupInstallButtonReinstall: String = "Reinstall"
    override val setupExitApp: String = "Exit App"
    override val setupAcceptAndContinue: String = "Accept and Continue"
    override val setupExtractionTitle: String = "Install Essential Components"
    override val setupExtractionDescription: String = "The launcher needs to install these components to ensure the game runs properly. These components are included within the app and need to be extracted before use."
    override val setupOverallProgressPreparing: String = "Preparing installation..."
    override val setupOverallProgressInstalling: String = "Installing..."
    override val setupOverallProgressVerifying: String = "Verifying files..."
    override val setupOverallProgressCompleted: String = "Installation completed"
    override val setupCheckAssets: String = "Preparing asset files..."
    override val setupCreateTargetDir: String = "Creating target directory..."
    override val setupStartExtracting: String = "Starting extraction of %s..."
    override val setupExtractingStructure: String = "Extracting file structure..."
    override val setupExtracting: String = "Extracting"
    override val setupExtractionFailedPrefix: String = "Extraction failed: %s"
    override val setupInvalidFilePathPrefix: String = "Invalid file path: %s"
    override val setupAssetFileMissingPrefix: String = "Asset file %s does not exist"
    override val setupExtractingSuccessful: String = "Extraction successful"
}