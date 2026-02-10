# Profile Conversion API Usage

## Overview

The Profile Conversion API provides functionality to convert various proxy profile formats to Clash format.

## Supported Formats

- **Clash/Clash Meta**: Native format (validation only)
- **Base64**: Base64-encoded proxy lists (ss://, vmess://, trojan://, etc.)
- **SIP008**: Shadowsocks JSON format

## API Components

### 1. ProfileFormat Enum

```kotlin
enum class ProfileFormat {
    CLASH, CLASH_META, BASE64, SHADOWROCKET,
    V2RAY, SIP008, UNKNOWN, AUTO
}
```

### 2. ConvertResult Data Class

```kotlin
data class ConvertResult(
    val success: Boolean,
    val content: String?,
    val format: String?,
    val error: String?
)
```

### 3. ProfileConverter Object

Main API for profile conversion.

## Usage Examples

### Auto-detect and Convert

```kotlin
import com.github.kr328.clash.core.bridge.ProfileConverter
import kotlinx.coroutines.launch

// In a coroutine scope
launch {
    val profileContent = """
        ss://base64encodeddata
        vmess://base64encodeddata
    """.trimIndent()

    val result = ProfileConverter.autoConvert(profileContent)

    if (result.isSuccess()) {
        println("Converted successfully!")
        println("Detected format: ${result.getDetectedFormat()}")
        val clashConfig = result.getClashConfig()
        // Use the config...
    } else {
        println("Conversion failed: ${result.getErrorMessage()}")
    }
}
```

### Detect Format Only

```kotlin
val content = "... profile content ..."
val format = ProfileConverter.detectFormat(content)

when (format) {
    ProfileFormat.BASE64 -> println("This is a Base64 subscription")
    ProfileFormat.SIP008 -> println("This is a SIP008 config")
    ProfileFormat.CLASH -> println("This is already a Clash config")
    else -> println("Unknown format")
}
```

### Convert Specific Format

```kotlin
launch {
    // Convert Base64 subscription
    val result = ProfileConverter.convertBase64ToClash(base64Content)

    // Convert SIP008
    val result2 = ProfileConverter.convertSIP008ToClash(sip008Json)

    // Convert with explicit format
    val result3 = ProfileConverter.convertToClash(
        content = myContent,
        sourceFormat = ProfileFormat.BASE64
    )
}
```

### Validate Clash Config

```kotlin
val clashConfig = """
port: 7890
socks-port: 7891
proxies:
  - name: test
    type: ss
    server: example.com
    port: 8388
    cipher: aes-256-gcm
    password: password
"""

val result = ProfileConverter.validate(clashConfig)
if (result.isSuccess()) {
    println("Valid Clash config!")
} else {
    println("Invalid: ${result.getErrorMessage()}")
}

// Or simple boolean check
val isValid = ProfileConverter.isValidClashProfile(clashConfig)
```

### Working with Files

```kotlin
import com.github.kr328.clash.core.bridge.ProfileUtils
import java.io.File

// Convert a file
launch {
    val inputFile = File("/path/to/subscription.txt")
    val result = ProfileUtils.convertFileToClash(inputFile)

    if (result.isSuccess()) {
        val outputFile = File("/path/to/config.yaml")
        ProfileUtils.saveToFile(result, outputFile)
    }
}

// Check if file is valid Clash config
val configFile = File("/path/to/config.yaml")
if (ProfileUtils.isValidClashFile(configFile)) {
    println("Valid Clash config file")
}

// Detect file format
val format = ProfileUtils.detectFileFormat(inputFile)
println("File format: ${ProfileUtils.getFormatDisplayName(format)}")
```

### Working with Streams

```kotlin
launch {
    val inputStream = context.contentResolver.openInputStream(uri)
    inputStream?.use { stream ->
        val result = ProfileUtils.convertStreamToClash(stream)
        // Handle result...
    }
}
```

## Error Handling

All conversion operations return a `ConvertResult` that indicates success or failure:

```kotlin
val result = ProfileConverter.autoConvert(content)

when {
    result.isSuccess() -> {
        // Success
        val config = result.getClashConfig()
        processConfig(config)
    }
    else -> {
        // Failure
        val errorMessage = result.getErrorMessage()
        showError(errorMessage)
    }
}
```

## Implementation Details

### Stage 1: JNI Bridge Functions (Completed)

- Go functions for profile parsing and conversion
- C JNI wrappers
- Native function declarations in Bridge.kt

### Stage 2: Kotlin Wrappers (Completed)

- ProfileFormat enum
- ConvertResult data class
- ProfileConverter object with high-level API
- ProfileUtils for file/stream operations

### Stage 3: Template System (Pending)

Will be implemented with custom logic after Stages 1 and 2 are tested.

## Testing

```kotlin
// Test auto-detection
val base64Content = "c3M6Ly8uLi4="
assert(ProfileConverter.detectFormat(base64Content) == ProfileFormat.BASE64)

// Test conversion
launch {
    val result = ProfileConverter.autoConvert(base64Content)
    assert(result.isSuccess())
    assert(result.getClashConfig().contains("proxies:"))
}
```

## Notes

- All conversion operations run on background threads (Dispatchers.IO)
- The `convertToClashBlocking` method is deprecated; use the suspend version instead
- Auto format detection is recommended for user imports
- Template system (Stage 3) will provide customizable config templates
