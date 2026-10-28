# Third-party notices

Dependencies are loaded from the player’s mods directory, never bundled in this JAR.
The scene geometry and integration were developed by consulting the following MIT projects.

## Cinematic Interactions

Source: https://github.com/dcvke/CinematicInteractions
Baseline: c79e1b9e05347165f6766700007cef45c8ce4d57 (0.18.12).
The add-on invokes the installed host’s hand renderer and SceneScreen drawer rendering/picking.
The camera adapter follows SceneScreen’s drawer layout formulas; no host JAR or textures are bundled.

MIT License

Copyright (c) 2026 Southware

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Exposure

Source: https://github.com/mortuusars/Exposure
Baseline: 08a8227ce7e824735970d8976c5040cd389b31fb (1.21.1 / 1.9.19).
Original models/textures are rendered through Exposure, not copied into the add-on.

MIT License

Copyright (c) 2025 mortuusars

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Gradle Wrapper

Gradle Wrapper is distributed under Apache License 2.0.
https://github.com/gradle/gradle/blob/v8.8.0/LICENSE

## Optional EMI compatibility

Source consulted: https://github.com/emilyploszaj/emi (1.21 branch).
The optional client Mixin invokes EMI's own empty-screen behavior; no EMI source or assets are bundled.
EMI remains an optional installed mod, not a required dependency.
