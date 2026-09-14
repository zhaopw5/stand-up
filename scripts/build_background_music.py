"""Generate a sample-free, deterministic soft keyboard loop. Requires numpy."""
from pathlib import Path
import json
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
RATE = 44100
BPM = 64
BEAT = 60 / BPM
BARS = 16
DURATION = BARS * 4 * BEAT
# MIDI pitches. All voicings and melody events are explicitly arranged here.
CHORDS = [
    [48,55,59,64], [45,52,59,60], [53,57,60,67], [43,50,57,62],
    [48,55,62,64], [40,47,55,62], [41,48,57,64], [43,50,55,59],
    [45,52,60,64], [41,48,55,60], [38,45,53,60], [43,50,57,62],
    [40,47,55,62], [41,48,57,60], [43,50,57,62], [48,55,59,62],
]
MELODY = [
    [(0.5,76),(2,74),(3.25,71)], [(1,72),(2.5,76)],
    [(0.75,74),(2,72),(3,69)], [(1,69),(2.75,71)],
    [(0.5,67),(2,74),(3.25,76)], [(1.25,74),(3,71)],
    [(0.5,72),(2.25,69),(3.5,67)], [(1,69),(2.5,71)],
    [(0.5,72),(1.75,76),(3,79)], [(1,76),(2.5,74)],
    [(0.5,77),(2,76),(3.25,72)], [(0.75,74),(2.5,71)],
    [(1,67),(2.5,71)], [(0.5,69),(2,72),(3.25,76)],
    [(0.75,74),(2.25,69)], [(0.5,71),(2,74)],
]


def main():
    rng = np.random.default_rng(20260914)
    events = []
    for bar, chord in enumerate(CHORDS):
        base = bar * 4
        events.append(dict(beat=base, midi=chord[0], velocity=0.49, part='bass'))
        for offset, voice in [(0.5,1),(1.5,2),(2.5,3),(3.5,2)]:
            events.append(dict(beat=base+offset, midi=chord[voice],
                               velocity=0.29+float(rng.uniform(-0.025,0.025)), part='arpeggio'))
        for offset, pitch in MELODY[bar]:
            events.append(dict(beat=base+offset, midi=pitch,
                               velocity=0.43+float(rng.uniform(-0.025,0.025)), part='melody'))
    n = round(DURATION * RATE)
    output = np.zeros((n, 2), dtype=np.float64)
    for event in events:
        frequency = 440 * 2 ** ((event['midi'] - 69) / 12)
        t = np.arange(round(5.5*RATE)) / RATE
        tone = np.zeros_like(t)
        # Decaying, slightly inharmonic partials create a soft struck-string timbre.
        for harmonic, strength in enumerate([1,.33,.14,.065,.03,.014], start=1):
            f = frequency * harmonic * np.sqrt(1 + .00008*harmonic**2)
            decay = (1.65 if event['part']=='bass' else 1.18) / harmonic**.48
            envelope = (1-np.exp(-t/.008))*np.exp(-t/decay)
            tone += strength*envelope*(np.sin(2*np.pi*f*t)+.18*np.sin(2*np.pi*f*1.0012*t))
        tone *= np.minimum((5.5-t)/.35,1).clip(0,1)
        tone *= event['velocity']*.13
        pan = np.clip((event['midi']-60)/65,-.30,.30)
        stereo = tone[:,None]*np.array([np.sqrt((1-pan)/2),np.sqrt((1+pan)/2)])
        # Wrap tails around the boundary, so the loop has continuous room decay.
        start = round(event['beat']*BEAT*RATE)
        indices = (start+np.arange(len(t))) % n
        output[indices] += stereo
    dry = output.copy()
    for delay, gain in [(.071,.10),(.113,.075),(.193,.055),(.307,.04),(.431,.025)]:
        output += np.roll(dry[:,::-1], round(delay*RATE), axis=0)*gain
    output -= output.mean(axis=0)
    # Quiet average level; leave ample headroom and avoid normalizing every note.
    rms = np.sqrt(np.mean(output**2))
    output *= min(10**(-24/20)/rms, .65/np.max(np.abs(output)))
    pcm = np.round(output*32767).astype('<i2')
    folder = ROOT/'music'; folder.mkdir(exist_ok=True)
    destination = folder/'quiet-steps-loop.wav'
    with wave.open(str(destination),'wb') as stream:
        stream.setnchannels(2);stream.setsampwidth(2);stream.setframerate(RATE)
        stream.writeframes(pcm.tobytes())
    score = dict(title='Quiet Steps / 慢步',bpm=BPM,bars=BARS,duration_seconds=DURATION,
                 sample_rate=RATE,seed=20260914,chords_midi=CHORDS,events=events)
    (folder/'quiet-steps-score.json').write_text(json.dumps(score,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    boundary = float(np.max(np.abs(output[0]-output[-1])))
    report = dict(duration_seconds=DURATION,notes=len(events),peak=float(np.max(np.abs(output))),
                  rms_dbfs=float(20*np.log10(np.sqrt(np.mean(output**2)))),
                  loop_boundary_step=boundary,clipped_samples=int(np.sum(np.abs(output)>=1)))
    assert np.isfinite(output).all() and report['clipped_samples']==0
    assert boundary < .02
    (folder/'audio-checks.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
