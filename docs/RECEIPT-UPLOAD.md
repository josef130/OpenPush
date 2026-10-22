# Receipt upload ordering

Reception is saved durably without starting a separate upload before the synchronous notification outcome. A successful Android notify stores displayed, then schedules the durable upload. Failure/disabled outcomes likewise schedule their own events. Delivered remains based on confirmed display; reception alone is not promoted to delivery.

ReceiptJobService refreshes its queue after each upload rather than iterating one snapshot. A display or click that arrives while an earlier request is in flight is drained in the same run (up to the existing 20-event limit), rather than unnecessarily waiting for JobScheduler's retry backoff. A newer session replacing an in-flight version is retained. Transport failure still preserves events and requests backoff; there is no retry loop that bypasses Android restrictions.

No changes were made to routing, PendingIntent identity, click deduplication, server capabilities or metric definitions. SDK unit contracts cover a display added during reception upload without a click, later independent click upload, durable retention on transport failure and concurrent session replacement.

45 SDK unit tests and SDK/sample debug builds passed on 2026-10-22. Physical test evidence is retained privately with the dashboard: a new image notification confirmed display about 0.95 seconds after creation, and its later click was recorded about 29 seconds after display. This is an observed local result, not a delivery latency guarantee. Offline devices, an unreachable endpoint or Android background constraints can delay analytics.
