#!/usr/bin/env python3
"""Patch MediaTek's IMS app in a tree decoded by apktool.

WifiPdnHandler tells the modem about Wi-Fi only when something about it changes, or on a radio
event. A change of airplane mode resets the modem's Wi-Fi state with nothing having changed, so the
modem has no address and Wi-Fi Calling never starts. Send it all with each change of Wi-Fi state.

It also learns of a connection from a network callback, which here reports only the network present
when it registers, so Wi-Fi Calling never returns after Wi-Fi reconnects. Take that from the
broadcast it already receives as well.

DisconnectingState only confirms a bearer release to the modem when it sees the IMS network go
away. AOSP tears that network down by itself on airplane mode, before the modem asks for the
release, so the event never comes and IMS stays stuck until reboot. Send the same message after
a delay, and cancel it on leaving the state.
usage: patch_ims_smali.py DECODED_DIR
"""
import sys

CLASS = "Lcom/mediatek/ims/internal/ImsDataSynchronizer$DataConnection$DisconnectingState;"
DC = "Lcom/mediatek/ims/internal/ImsDataSynchronizer$DataConnection;"
MACHINE = "Lcom/android/internal/util/StateMachine;"
MSG_NETWORK_LOST = "0x3"
DELAY_MS = "0x5dc"  # 1500 ms

path = sys.argv[1] + "/smali/com/mediatek/ims/internal/ImsDataSynchronizer$DataConnection$DisconnectingState.smali"
s = open(path).read()


def patch(method, locals_count, code):
    global s
    start = s.index(f".method public {method}()V")
    end = s.index(".end method", start)
    body = s[start:end]
    if "    .locals 2\n" not in body or "    return-void\n" not in body:
        sys.exit(f"unexpected {method}()")
    body = body.replace("    .locals 2\n", f"    .locals {locals_count}\n")
    body = body.replace("    return-void\n", code + "\n    return-void\n")
    s = s[:start] + body + s[end:]


patch("enter", 4, f"""    iget-object v0, p0, {CLASS}->this$1:{DC}

    const/4 v1, {MSG_NETWORK_LOST}

    const-wide/16 v2, {DELAY_MS}

    invoke-virtual {{v0, v1, v2, v3}}, {MACHINE}->sendMessageDelayed(IJ)V
""")
patch("exit", 2, f"""    iget-object v0, p0, {CLASS}->this$1:{DC}

    invoke-virtual {{v0}}, {MACHINE}->getHandler()Landroid/os/Handler;

    move-result-object v0

    const/4 v1, {MSG_NETWORK_LOST}

    invoke-virtual {{v0, v1}}, Landroid/os/Handler;->removeMessages(I)V
""")
open(path, "w").write(s)
print("patched DisconnectingState")

HANDLER = "Lcom/mediatek/wfo/impl/WifiPdnHandler;"
path = sys.argv[1] + "/smali/com/mediatek/wfo/impl/WifiPdnHandler.smali"
s = open(path).read()
start = s.index(".method private setWifiEnabled()V")
end = s.index(".end method", start)
body = s[start:end]
if body.count("    return-void\n") != 1:
    sys.exit("unexpected setWifiEnabled()")
body = body.replace("    return-void\n", f"""    iget-boolean v0, p0, {HANDLER}->mIsWifiConnected:Z

    if-eqz v0, :jelly_no_address

    invoke-direct {{p0}}, {HANDLER}->setWifiAssoc()V

    invoke-direct {{p0}}, {HANDLER}->setWifiSignalLevel()V

    invoke-direct {{p0}}, {HANDLER}->setWifiIpAddress()V

    :jelly_no_address
    return-void
""")
s = s[:start] + body + s[end:]
open(path, "w").write(s)
print("patched WifiPdnHandler")

RECEIVER = "Lcom/mediatek/wfo/impl/WifiPdnHandler$1;"
STATE = "Lcom/mediatek/wfo/impl/WifiPdnHandler$WifiConnState;"
path = sys.argv[1] + "/smali/com/mediatek/wfo/impl/WifiPdnHandler$1.smali"
s = open(path).read()
store = f"""    iget-object v1, p0, {RECEIVER}->this$0:{HANDLER}

    invoke-static {{v1, v2}}, {HANDLER}->-$$Nest$fputmOldWifiConnectedFromNetworkInfo({HANDLER}Z)V
"""
if s.count(store) != 1:
    sys.exit("unexpected WifiPdnHandler$1")
s = s.replace(store, f"""    if-eqz v2, :jelly_known_state

    iget-object v3, p0, {RECEIVER}->this$0:{HANDLER}

    invoke-static {{v3}}, {HANDLER}->-$$Nest$fgetmIsWifiConnected({HANDLER})Z

    move-result v3

    if-nez v3, :jelly_known_state

    iget-object v3, p0, {RECEIVER}->this$0:{HANDLER}

    sget-object v4, {STATE}->DEFAULT_NETWORK_VALIDATED_CONNECTED:{STATE}

    invoke-virtual {{v4}}, {STATE}->ordinal()I

    move-result v4

    const/16 v5, 0x3e8

    const/4 v1, 0x0

    const/4 v6, 0x0

    invoke-virtual {{v3, v5, v4, v1, v6}}, {HANDLER}->obtainMessage(IIILjava/lang/Object;)Landroid/os/Message;

    move-result-object v1

    invoke-virtual {{v3, v1}}, {HANDLER}->sendMessage(Landroid/os/Message;)Z

    :jelly_known_state
""" + store)
open(path, "w").write(s)
print("patched WifiPdnHandler$1")
