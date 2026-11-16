# SDL3 Vulkan swapchain regression check

The Pixel 5 / Android 14 report (Adreno 620, Qualcomm Vulkan driver built
2020-11-30) selected Vulkan and emitted `Opaque presentation unsupported` and
unsupported 4x4 AHardwareBuffer format probes. Later the driver ran out of GPU
memory and crashed. The log alone does not establish that the probes caused
the allocation failure.

SDL now selects a composite alpha mode from the surface's capabilities, using
the [upstream SDL selection order](https://github.com/libsdl-org/SDL/blob/main/src/gpu/vulkan/SDL_gpu_vulkan.c).
This satisfies Vulkan's `VUID-VkSwapchainCreateInfoKHR-compositeAlpha-01280`.
Image acquisition also returns device/allocation failures immediately; surface
loss/out-of-date errors get one recreation attempt per call, then defer to the
next frame instead of looping without a bound.

The check extracts and compiles the actual selection and acquisition code from
the SDL submodule. A fake Vulkan driver covers all nonempty alpha capability
masks, transparent/opaque windows, successful acquisition, device loss, memory
exhaustion, and recoverable/persistent surface changes. It runs an isolated
arm64 executable on ADB without starting or stopping an app.

```powershell
python scripts/sdl3/check_vulkan_swapchain.py --ndk D:/Android/ndk/29.0.14206865 --serial DEVICE_SERIAL
./gradlew.bat :app:assembleRelease --console=plain
```

The old code fails the supported-alpha assertion. The repaired code passes
30 alpha cases and 7 acquisition cases. Full-game validation on the affected
Pixel 5 is still needed: select Vulkan, launch to the menu, enter a world, and
test background/resume. Inspect fresh logs for swapchain errors, GPU memory
exhaustion, and native crashes. Android's unsupported AHardwareBuffer format
probes can still be logged by the driver; this change does not hide them.

No settings or save migration is required.
