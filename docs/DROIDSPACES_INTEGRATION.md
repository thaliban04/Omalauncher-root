# DroidSpaces Integration Guide

OmaLauncher supports two Linux backends:

| Backend | Root Required | Performance | Init System |
|---------|--------------|-------------|-------------|
| **PRoot** (default) | ❌ No | ~75% native | Manual shell |
| **DroidSpaces** | ✅ Yes | ~98% native | systemd / OpenRC |

## How DroidSpaces Works

DroidSpaces uses true Linux kernel namespaces (PID, Mount, Network, IPC, UTS) to run
a full Linux distribution natively on Android — no ptrace emulation, no performance overhead.

```
Android Kernel
    └── DroidSpaces Runtime (/data/local/Droidspaces/bin/droidspaces)
            └── Linux Container (Arch / Ubuntu / Debian)
                    ├── systemd (PID 1)
                    ├── Linux apps...
                    └── Wayland → OmaLauncher compositor (JNI/wlroots)
```

## Requirements

Your device needs:
- **Root**: KernelSU (recommended), Magisk, or APatch
- **Kernel** compiled with:
  - `CONFIG_NAMESPACES=y` (PID, Mount, Net, IPC, UTS, User)
  - `CONFIG_CGROUPS=y` + cgroup subsystems
  - `CONFIG_OVERLAY_FS=y`
  - `CONFIG_SECCOMP=y`

The app automatically checks kernel compatibility at setup.

## Building with DroidSpaces Support

Before building, fetch the DroidSpaces binary:

```bash
./scripts/fetch-droidspaces.sh
```

This downloads the latest `droidspaces` static binary from
[ravindu644/Droidspaces-OSS](https://github.com/ravindu644/Droidspaces-OSS/releases)
and places it in `app/src/main/assets/droidspaces-arm64`.

Then build normally:

```bash
./gradlew :app:installDebug
```

## Runtime Behavior

At first launch, OmaLauncher:
1. Checks if root is available and kernel supports namespaces
2. If **DroidSpaces mode**: deploys binary → `BackendDetector` → `DroidSpacesBackend`
3. If **PRoot mode**: uses existing proot flow → `Proot` class

The launcher shows a **🐧 Native** or **🐧 PRoot** badge on the home screen.

## Wayland Display

Both backends use OmaLauncher's built-in **wlroots-based Wayland compositor** (JNI).

For DroidSpaces, the Wayland socket is bind-mounted into the container at:
```
/run/user/0/wayland-0  (inside container)
  → /data/user/0/dev.omalauncher/files/xdg/wayland-0  (Android side)
```

Each Linux GUI app opens as a separate Android task (Recents card) — same as PRoot mode.

## Networking

DroidSpaces containers get a full network namespace with NAT by default.
Internet access works via `veth` pair + iptables masquerading.

## Troubleshooting

**"Root not available"**: Grant root to OmaLauncher in KernelSU/Magisk/APatch settings.

**"Missing kernel features"**: Your kernel wasn't compiled with namespace support.
Check [DroidSpaces kernel guide](https://github.com/ravindu644/Droidspaces-OSS#kernel-requirements).

**"Binary not found"**: Run `./scripts/fetch-droidspaces.sh` and rebuild.

**Container won't start**: Check logs via `su -c "/data/local/Droidspaces/bin/droidspaces status"`.
