# BBS AI Animator (prototype, BBS FS 2.5.2, Fabric 1.20.1)

Walk around morphed into a BBS **model form**, tell an AI how the character should move
("runway model strut", "sneaky ninja", "tired old man"), and watch the model animate live.

## How it works
An AI cannot write animation frames 60 times a second, so the work is split:

- **The AI is the animation director.** In the background it writes batches of step variations
  (left and right foot, arm swing, hip sway, head tilt, stride, bob) for the *exact bones of your model*.
- **The mod animates live.** Every rendered frame it follows how far you actually walked, plays the
  variations step by step, blends them smoothly, adds small random changes per step, and asks the AI for
  fresh batches every ~15 s so the pool keeps changing.

The first steps appear a few seconds after you set a style. Until then your model uses its normal animation.

## Setup
1. Install in `mods/` next to BBS FS 2.5.2 and Fabric API.
2. Get an Anthropic API key. In game run `/aianim key <your key>`, or set the `ANTHROPIC_API_KEY`
   environment variable, or edit `config/bbs_ai_animator.json`. **Never commit that file or share the key.**
3. Morph into a BBS model form.
4. `/aianim style runway model strut` and start walking (third person, F5, to see yourself).

## Commands
| Command | What it does |
|---|---|
| `/aianim style <text>` | Set the style (any description) and turn it on |
| `/aianim on` / `off` | Toggle |
| `/aianim reroll` | Throw away the current steps and ask again |
| `/aianim key <key>` | Save your API key |
| `/aianim model <name>` | Which AI model to use (default `claude-haiku-5-5`, fast and cheap) |
| `/aianim strength <0-3>` | Scale all angles |
| `/aianim steplength <blocks>` | Blocks per step |
| `/aianim flip` | Negate the swing if limbs move backward on your model |
| `/aianim status` | Debug info |

## Cost
Each batch is a small request (a few cents or less with Haiku). It asks again every ~15 s while you walk and stops when the model is idle.

## Limitations (v1)
- Client side only: you see your own animation, other players do not.
- It layers on top of the model's own animation through BBS's pose overlay.
- Rig conventions differ, so axes can be off on some models (see `/aianim flip` and `/aianim strength`).

## Build on GitHub
Push to a new repo (no README), open **Actions**, download the `bbs-ai-animator` artifact, use `bbs-ai-animator-1.0.0.jar` (not `-sources`).
