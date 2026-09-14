# recorder

Records stage-1 hand-pose exemplars for the Handspell k-NN classifier and writes them straight to
`android/app/src/main/assets/classifier/references-v1.csv` — the only file `KnnLetterClassifier`
reads.

It exists because the in-app debug capture screen (`docs/CLASSIFIER.md` §7) is not written yet, and
without that data the app's camera screen sits in its `DetectorStatus.Failed` state. This is the
desktop stand-in for that screen.

Full documentation, including what it deliberately does not do: [`docs/RECORDER.md`](../docs/RECORDER.md).

## Setup and run (Windows)

Double-click `setup.bat` once — it creates `.venv/` in this folder via
[uv](https://docs.astral.sh/uv/) — then:

```
run.bat --selftest     # no camera: proves the pipeline works
run.bat                # record
```

To measure how well the recorded set actually recognises hands, use the leave-one-out report in the
desktop preview: `preview-windows\run.bat --eval` (`docs/WINDOWS_PREVIEW.md`). The classifier lives in
one place on purpose, and this folder is not it.

## Setup and run (any OS)

`setup.bat` and `run.bat` are Windows conveniences over three commands. On Linux or macOS, where
`docs/DEV_SETUP.md` already assumes `uv`:

```bash
cd recorder
uv python install 3.12
uv venv .venv --python 3.12
uv pip install --python .venv/bin/python -r requirements.txt
.venv/bin/python record_references.py --selftest
```

On Windows the interpreter path is `.venv\Scripts\python.exe` instead of `.venv/bin/python`.

## Controls

| Key | Action |
| --- | --- |
| `a`–`z` | record the current pose as that letter |
| `Enter` | save, then copy the saved file to the clipboard |
| `Backspace` | undo the last sample |
| `Tab` | toggle the dropped-sample readout |
| `-` | toggle mirroring |
| `Esc` | save and quit |

Each keypress records one sample, so a letter can hold many exemplars — up to the spec's cap of 64.
Near-identical poses are dropped automatically. J and Z are excluded: they are produced with motion
and have no single-frame handshape.
