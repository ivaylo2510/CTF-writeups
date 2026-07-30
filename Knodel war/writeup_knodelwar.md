# Knodel War!

## Introduction

The Internet of Things (IoT) has revolutionized how everyday devices connect and interact, but this connectivity comes with significant security challenges. As the course slides emphasize, "The S in IoT Stands for Security" is a running joke in the security community—and for good reason. IoT devices are often shipped with security as an afterthought, short development cycles lead to copy-pasted code, and devices remain in the field for 5-10+ years with outdated software.

One particularly vulnerable IoT ecosystem is HbbTV (Hybrid Broadcast Broadband TV) — a technology that combines traditional broadcast TV with internet connectivity. HbbTV applications are embedded as URLs within broadcast streams and executed by smart TVs. This architecture creates a unique attack surface: if an attacker can modify the broadcast stream (via DVB/DMS-CC injection or signal replacement), they can inject malicious URLs and compromise thousands of viewers simultaneously. Real-world examples include the 2022 hack of Russian smart TVs displaying anti-war messages, and researchers demonstrating URL injection attacks using drones and SDR equipment.

Knodel war! demonstrates exactly why HbbTV is so dangerous. In this challenge, "malicious chefs" have hijacked a TV broadcast to show the wrong knodel recipe. Our task is to analyze the recorded DVB transport stream, extract the HbbTV application data, and identify which recipe is being displayed.

## Reconnaissance & Analysis

### Initial Observation

After extracting the provided stream file (`hbbtv_chal.ts`), our first instinct was to inspect the video content itself. Playing the file revealed a cooking show featuring Italian chefs making knödels. However, the dialogue was in Italian, and nothing visually obvious indicated a "wrong recipe" or malicious content.

### Theoretical Approach

This observation led us to recall the core concepts of HbbTV Security from the lectures. HbbTV works by delivering interactive content (HTML/JS applications) alongside the traditional broadcast signal. Crucially, the "trigger" for these applications is embedded in the broadcast metadata, specifically in the Application Information Table (AIT).

In a DVB Hijack attack, the attacker doesn't necessarily need to replace the entire video stream (which is bandwidth-intensive and difficult). Instead, they can simply inject or modify the AIT to point the victim's TV to a malicious URL. This URL then loads the fake content (in this case, the "wrong recipe") as an overlay on top of the legitimate video.

### Strategy

Based on this understanding, our strategy shifted from video analysis to signal analysis. We needed to:

- Ignore the video/audio stream.
- Analyze the DVB tables hidden within the transport stream.
- Specifically target the AIT to find the URL or parameters of the injected HbbTV application.
- Extract this data to reveal the flag, which likely represents the malicious payload.

To execute this, we chose TSDuck, a powerful tool for manipulating MPEG transport streams, as it allows us to parse these binary tables into a human-readable format.

## Understanding the Application Information Table (AIT)

### What is the AIT?

The Application Information Table (AIT) is a critical component of the DVB (Digital Video Broadcasting) standard, specifically designed for HbbTV applications. The AIT is a data structure embedded within the transport stream that tells compatible smart TVs:

- Which application to launch (via a URL or application identifier)
- When to launch it (e.g., immediately on channel change, or triggered by user interaction)
- How the application should behave (e.g., run in the background, overlay the video, or take full control)
- Application parameters (e.g., version numbers, permissions, initialization data)

### Structure of the AIT

The AIT contains several key descriptors that define the HbbTV application:

- **Application Descriptor:** Contains the application's control code (`AUTOSTART`, `PRESENT`, etc.) and visibility settings
- **Transport Protocol Descriptor:** Defines how the application is delivered (HTTP, HTTPS, DVB carousel, etc.)
- **Simple Application Location Descriptor:** The most important for our purposes — this contains the base URL of the HbbTV application and the initial path to load
- **Application Name Descriptor:** Human-readable name of the application
- **Application Usage Descriptor:** Defines the intended usage context

In a DVB hijack attack, the attacker modifies the Simple Application Location Descriptor to point to their malicious server instead of the legitimate broadcaster's URL.

### How to Extract the AIT

Since the AIT is binary data embedded in the MPEG transport stream, we need specialized tools to extract and decode it. TSDuck provides the `tstables` utility specifically for this purpose.

The command to extract all tables (including the AIT) from a transport stream and convert them to XML format is:

```
tstables --xml-output ait.xml hbbtv_chal.ts
```

## Analysis of the Extracted XML Structure

The extracted XML file provides a complete view of the DVB metadata contained within the transport stream. This metadata is organized into specific tables, each serving a distinct purpose in the broadcast ecosystem. Below is an analysis of the key tables found in our output.

### 1. Service Description Table (SDT)

The SDT lists the services (channels) available in the transport stream. In our XML, the SDT reveals that this stream contains a multiplex of Italian channels from the provider "Persidera".

**Example from our XML:**

```xml
<service service_id="0x0032" running_status="running">
    <service_descriptor service_provider_name="Persidera" service_name="Motor Trend"/>
</service>
```

Analysis: We can see services like "Motor Trend", "ALMA TV", "Radio Zeta", and "Kiss Kiss TV". The `running_status="running"` indicates these channels were active.

### 2. Program Map Table (PMT)

The PMT defines the specific elementary streams (video, audio, data) that make up a single service. It links a `service_id` to the Packet Identifiers (PIDs) carrying the actual content.

**Example from our XML:**

```xml
<PMT version="10" current="true" service_id="0x005A">
    <!-- Video Stream -->
    <component elementary_PID="0x0141" stream_type="0x02"/>
    <!-- Audio Stream -->
    <component elementary_PID="0x0142" stream_type="0x03"/>
    <!-- HbbTV Signaling -->
    <application_signalling_descriptor application_type="0x0010"/>
</PMT>
```

Analysis: This PMT entry shows a video stream, an audio stream, and crucially, an `application_signalling_descriptor`. This descriptor is the flag that tells the TV: "There is an interactive HbbTV application available for this channel."

### 3. Application Information Table (AIT)

This is the most critical table for our challenge. As defined in ETSI TS 102 809, the AIT carries the actual instructions for launching the interactive application. Our XML contains multiple AIT sections.

**Example: Standard HbbTV App (Radio Zeta)**

```xml
<AIT version="5" current="true">
    <application control_code="0x01"> <!-- AUTOSTART -->
        <application_name_descriptor>
            <language code="eng" application_name="HbbTV - RADIOZETA"/>
        </application_name_descriptor>
        <transport_protocol_descriptor>
            <http>
                <url base="https://cdn.radiozeta.it/hbbtv.radiozeta.it/"/>
            </http>
        </transport_protocol_descriptor>
        <simple_application_location_descriptor initial_path="index.html"/>
    </application>
</AIT>
```

Analysis: This is a legitimate HbbTV app.

- **Control Code 0x01:** AUTOSTART (starts automatically).
- **URL Base:** Points to a valid HTTPS domain.
- **Mechanism:** The TV concatenates the Base URL + Initial Path to fetch the app.

### 4. Event Information Table (EIT)

The EIT provides the Electronic Program Guide (EPG) data — what shows are currently playing.

**Example from our XML:**

```xml
<event event_id="0x5664" start_time="2021-02-13 16:20:00">
    <short_event_descriptor language_code="ita" event_name="GUINNESS 6 Nazioni live"/>
</event>
```

Analysis: This confirms the recording was taken on Feb 13, 2021, during a "Guinness 6 Nations" rugby match broadcast.

### Summary of Findings

The XML structure reveals a standard DVB broadcast environment with multiple channels. Each channel has Video/Audio streams (PMT), Programme info (EIT), and Interactive Apps (AIT). The detailed AIT sections confirm that this stream is heavily utilizing HbbTV, making it the primary vector for hiding the flag.

## Identifying the Malicious Payload

After parsing the XML structure and filtering through the standard broadcast data, we turned our attention to finding the anomaly described in the challenge — the "wrong recipe" application.

Scanning the Application Information Tables (AIT), we found several standard entries for known channels (e.g., "HbbTV - RADIOZETA", "HbbTV - MOTORTREND"). However, one entry stood out immediately due to its suspicious metadata.

### The Suspicious Entry

We located an application explicitly named **"Knodel War!"**. Upon closer inspection of its descriptors, two fields appeared highly irregular compared to the standard entries:

1. **The URL Base:** Instead of a valid HTTP/HTTPS domain (like `https://cdn.radiozeta.it/...`), the `url base` field contained a plain text string:

```xml
<url base="almost there, maybe use a different encoding..."/>
```

This is technically invalid for a URL and serves as a clear hint from the challenge author that the next step involves decoding.

2. **The Initial Path:** The `initial_path` field, which normally points to a file like `index.html` or `loader.php`, contained a long, random-looking string of characters:

```xml
<simple_application_location_descriptor initial_path="RkxHX1BUM3tQMW4zQXBwbDNfS24wZDNsfQ"/>
```

### Analyzing the String

The string `RkxHX1BUM3tQMW4zQXBwbDNfS24wZDNsfQ` has distinct characteristics that suggest it is an encoded payload rather than a valid file path:

- **Character Set:** It consists of alphanumeric characters (A-Z, a-z, 0-9).
- **Length:** It is a single, continuous string without file extensions (like `.html`).
- **Structure:** It matches the pattern of Base64 encoding (commonly used to obfuscate data).

Given the hint "use a different encoding" found in the URL base, we can confidently conclude that this string is the flag encoded in Base64.

### Decoding

To retrieve the final flag, we simply need to decode this string using a standard Base64 decoder — you can even find one on Google, like [base64decode.org](https://www.base64decode.org/).

Decoding `RkxHX1BUM3tQMW4zQXBwbDNfS24wZDNsfQ` yields the flag `FLG_PT3{P1n3Appl3_Kn0d3l}` — a fitting punchline, since a pineapple-knödel recipe is indeed the "wrong" one that the malicious chefs injected into the broadcast.

## References

- ETSI TS 102 809 V1.3.1 (2017-06) — *Digital Video Broadcasting (DVB); Signalling and carriage of interactive applications and services in Hybrid Broadcast/Broadband environments*. Available at: https://www.etsi.org/deliver/etsi_ts/102800_102899/102809/01.03.01_60/ts_102809v010301p.pdf
- HbbTV Developer Guide: *Building a Broadcast AIT* — Official HbbTV Association Developer Documentation. Available at: https://developer.hbbtv.org/guide/launching-hbbtv-applications-from-a-broadcast-channel/building-a-broadcast-ait/
- TSDuck User Guide — *The MPEG Transport Stream Toolkit Documentation*. Available at: https://tsduck.io/docs/tsduck.pdf
- Lecture Slides: IoT Security (2025W) — Slides 47-52: HbbTV Security & DVB Hijack, Course Material: Foundation of Systems and Application Security
