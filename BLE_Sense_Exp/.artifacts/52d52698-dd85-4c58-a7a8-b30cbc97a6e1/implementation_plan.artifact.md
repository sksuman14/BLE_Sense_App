# Implementation Plan - Robot Control UI Enhancements

This plan addresses UI improvements in `RobotControlCompose.kt`, including increasing button sizes, updating central button interaction logic, and refining the buzzer hold-to-buzz behavior.

## Proposed Changes

### [Robot Control UI]

#### [MODIFY] [RobotControlCompose.kt](file:///C:/Users/1239/Desktop/BLE_Sense_Exp%20140826(1)/BLE_Sense_Exp%20310726(3)/BLE_Sense_Exp/app/src/main/java/com/blesense.app/view/screens/RobotControlCompose.kt)

- **Button Size Increase**:
  - Update `HexControlPanelHUD` to increase button size from `68.dp` to `85.dp` and icon size from `34.dp` to `42.dp`.
  - Increase `HexControlPanelHUD` width from `170.dp` to `210.dp` in `RobotControlScreen` calls to accommodate larger buttons.
  - Increase text size for button labels in `HexControlPanelHUD` from `9.sp` to `11.sp`.
- **Central Button & Telemetry Logic**:
  - Remove the telemetry menu opening behavior (`showSensorMenuDialog = true`) from the `MoreVert` button in the header.
  - Add a `.clickable` modifier to the `CyberRoverGraphicHUD` (the central vehicle graphic) to allow disconnecting the Bluetooth device when clicked.
  - Ensure the "Capsule Pill Status Bar" (center of header) only performs disconnection logic and does not open telemetry.
- **Buzzer Refinement**:
  - Verify and ensure the buzzer button correctly sends the "H" (Horn/Start) command on press and "C" (Close/Stop) command on release using `pointerInput`.

## Verification Plan

### Manual Verification
- Deploy the app to a device and navigate to the Robot Control screen.
- Verify that Forward, Backward, Left, and Right buttons are significantly larger.
- Verify that clicking the central vehicle graphic or the status pill disconnects the robot car.
- Verify that the `MoreVert` button no longer opens the sensor telemetry dialog.
- Verify that holding the buzzer button sends a start command and releasing it sends a stop command (monitored via logs or hardware feedback).
