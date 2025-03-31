## Download Latest Release
📦 **Latest Version**: [v1.0.2](https://github.com/qbit-spark/INSTANTLY-SMS-CATCH/releases/tag/v1.0.2)  
🔔 *Check releases page for updates and changelog*

---

## JSON Structure for SMS Message Data

This service captures SMS messages along with comprehensive device information. The `deviceDetails` object supports dynamic fields, allowing flexibility for changing device attributes.

### Sample Request Body
```json
[
  {
    "branchId": "1234",
    "sender": "KIBUTI BOT",
    "message": "Karibu Trimness security, tukufanyie usafi mzuri",
    "timestamp": "2023-07-02T08:20:00Z",
    "deviceDetails": {
      "hardwareDetails": {
        "model": "Pixel 4a",
        "manufacturer": "Google",
        "device": "sunfish",
        "product": "sunfish",
        "board": "sunfish",
        "hardware": "sunfish",
        "screenResolution": "1080 x 2138",
        "screenDensity": 440,
        "screenSize": "5.4",
        "totalRam": "5.47 GB",
        "availableRam": "2.10 GB",
        "totalStorage": "109.59 GB",
        "availableStorage": "40.99 GB",
        "cpuModel": "sunfish",
        "processorCores": 8
      },
      "androidVersionDetails": {
        "androidVersion": "13",
        "apiLevel": 33,
        "securityPatch": "2023-08-05",
        "kernelVersion": "4.14.302-g92e0d94b6cba",
        "buildId": "TQ3A.230805.001.S2",
        "buildTime": 1731578876000,
        "fingerprint": "google/sunfish/sunfish:13/TQ3A.230805.001.S2/12655424:user/release-keys"
      },
      "deviceIdentifiers": {
        "androidId": "4110b69313cbb12f",
        "imei": "Permission not granted",
        "serialNumber": "Permission not granted",
        "macAddress": "02:00:00:00:00:00"
      },
      "networkInformation": {
        "connected": true,
        "connectionType": "WIFI",
        "ipAddress": "fe80::ce89:7940:fa40:6470%rmnet_data0"
      },
      "batteryInformation": {
        "chargingStatus": "Full",
        "batteryLevel": "100.0%",
        "batteryHealth": "Unknown",
        "batteryTemperature": "33.6°C"
      }
    }
  }
]
```

---

## Field Reference

### Message Metadata
| Field       | Type   | Description                          | Example                    |
|-------------|--------|--------------------------------------|----------------------------|
| `branchId`  | String | Branch identifier                   | "1234"                     |
| `sender`    | String | Name of message sender              | "KIBUTI BOT"               |
| `message`   | String | SMS message content                 | "Karibu Trimness security" |
| `timestamp` | String | ISO-8601 formatted timestamp        | "2023-07-02T08:20:00Z"     |

### Device Details
#### Hardware Information
- `model`, `manufacturer`: Device make and model
- `screen*`: Display characteristics
- `*Ram`, `*Storage`: Memory metrics
- `cpu*`: Processor information

#### Android Version
- `androidVersion`: Human-readable version
- `apiLevel`: SDK version
- `securityPatch`: Patch date
- `build*`: Build identifiers

#### Identifiers
⚠️ *Note: Many identifiers require special permissions on modern Android versions*
- `androidId`: Persistent but resettable ID
- `imei`, `serialNumber`: Often restricted
- `macAddress`: Returns generic value on Android 10+

#### Network Status
- Current connection type and IP address

#### Battery Status
- Charge level, health, and temperature

---

## Important Notes
1. **Dynamic Fields**: The `deviceDetails` object may contain additional fields not documented here
2. **Android Restrictions**: 
   - Many device identifiers are restricted on Android 10+
   - Requires `READ_PHONE_STATE` and other permissions
---
