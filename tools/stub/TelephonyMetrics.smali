.class public Lcom/android/internal/telephony/metrics/TelephonyMetrics;
.super Ljava/lang/Object;

# No-op stand-in for the class AOSP r4 no longer ships in telephony-common.
# MTK ImsService only calls these five logging methods.

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static getInstance()Lcom/android/internal/telephony/metrics/TelephonyMetrics;
    .registers 1
    new-instance v0, Lcom/android/internal/telephony/metrics/TelephonyMetrics;
    invoke-direct {v0}, Lcom/android/internal/telephony/metrics/TelephonyMetrics;-><init>()V
    return-object v0
.end method

.method public writeOnRilSolicitedResponse(IIIILjava/lang/Object;)V
    .registers 6
    return-void
.end method

.method public writeOnRilTimeoutResponse(III)V
    .registers 4
    return-void
.end method

.method public writeRilAnswer(II)V
    .registers 3
    return-void
.end method

.method public writeRilSendSms(IIIIJ)V
    .registers 7
    return-void
.end method
