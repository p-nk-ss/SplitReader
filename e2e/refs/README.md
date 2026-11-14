# Reference screenshots

Small reference crops for flows that have to check something the accessibility tree does not
carry. Only one user so far:

## `theme/` — is the screen dark or light? (`e2e/flows/reading/J-RDR-05.yaml`)

A reader theme is only colours; no node says which theme is on. J-RDR-05 therefore compares a
small crop of each screen (one fixed text: "Your shelf", "All time", "0 saved words",
"Page margins", "Display", the first chapter's title) with `assertScreenshot` against

```
theme/<device class>-<screen>-<paper|sepia|night>.png      device class = tablet | phone
```

What this can and cannot tell (measured with Maestro 2.12.1):

- Night against Paper: about 1% match — a dark screen never passes a light reference, nor the
  other way round.
- Sepia against Paper, Amoled against Night: 100% match even at `thresholdPercentage: 100` — the
  comparison tolerates small colour differences. So the checks mean "dark" / "light", never "this
  exact theme"; `…-display-sepia.png` is a Sepia crop that a Paper dialog would pass too.
- The crops are pixels of `Tab_35_PixelTablet` (tablet) and `Pixel_10_Pro` (phone). A device with
  another size or density gives crops of another size and fails; record its own set.

### Re-recording

After a change to the fonts, the palettes or those texts:

```
e2e/run.sh --avd Tab_35_PixelTablet --tags J-LIB-19 --keep-emulator   # any run: boots, installs
e2e/refs/record_theme_refs.sh emulator-5554
```

(the same on `Pixel_10_Pro`). The script runs J-RDR-05 with every `assertScreenshot` turned into a
`takeScreenshot` of the same crop and copies the crops here. Look at the new PNGs before committing
them — a reference recorded from a wrong screen makes the journey pass on that wrong screen.
