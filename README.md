# JSON Structure for SMS Message Data

## Overview
It supports dynamic fields in `deviceDetails`, allowing flexibility for changing device attributes.

## JSON Structure (request body example)
```json
[
  {
    "branchId": "1234",
    "sender": "KIBUTI BOT",
    "message": "Karibu Trimness security, tukufanyie usafi mzuri",
    "timestamp": "+57216-07-02T08:20:00Z",
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

## Field Descriptions
### **Root Level Fields**
- `branchId`: (String) The branch identifier where the message originates.
- `sender`: (String) Name of the message sender.
- `message`: (String) SMS message content.
- `timestamp`: (String) The date and time when the message was sent.

### **deviceDetails** (Dynamic Object)
Contains information about the device sending the SMS. This structure can change over time, as fields may be added or removed dynamically.

#### **hardwareDetails**
- `model`: (String) Model of the device.
- `manufacturer`: (String) Device manufacturer.
- `device`: (String) Internal device name.
- `screenResolution`: (String) Screen resolution in pixels.
- `screenDensity`: (Integer) Screen density in DPI.
- `totalRam`: (String) Total RAM available.
- `availableRam`: (String) Free RAM available.
- `totalStorage`: (String) Total internal storage.
- `availableStorage`: (String) Available storage space.
- `cpuModel`: (String) CPU model of the device.
- `processorCores`: (Integer) Number of processor cores.

#### **androidVersionDetails**
- `androidVersion`: (String) Android OS version.
- `apiLevel`: (Integer) API level of the Android OS.
- `securityPatch`: (String) Date of the latest security patch.
- `kernelVersion`: (String) Kernel version running on the device.
- `buildId`: (String) OS build identifier.
- `buildTime`: (Long) Timestamp of when the build was created.
- `fingerprint`: (String) Device fingerprint identifier.

#### **deviceIdentifiers**
- `androidId`: (String) Unique Android ID of the device.
- `imei`: (String) IMEI number (if permission is granted).
- `serialNumber`: (String) Device serial number (if permission is granted).
- `macAddress`: (String) MAC address of the device.

#### **networkInformation**
- `connected`: (Boolean) Whether the device is connected to a network.
- `connectionType`: (String) Type of network (e.g., WiFi, Mobile Data).
- `ipAddress`: (String) IP address of the device.

#### **batteryInformation**
- `chargingStatus`: (String) Charging status (e.g., Charging, Full).
- `batteryLevel`: (String) Current battery percentage.
- `batteryHealth`: (String) Battery health status.
- `batteryTemperature`: (String) Battery temperature in Celsius.

## Notes
- The `deviceDetails` section is dynamic and can change over time. Any additional fields not listed here may still be received and stored.

