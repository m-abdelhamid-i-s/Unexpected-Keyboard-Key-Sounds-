package juloo.keyboard2;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

/** Native feedback for on-screen key activations. No audio focus or volume
    override: Android owns the effects, touch-sound preference and audio policy. */
public final class KeypressSound
{
  private final Playback _playback;

  public KeypressSound(Context context)
  {
    this(new NativePlayback(context));
  }

  KeypressSound(Playback playback)
  {
    _playback = playback;
  }

  public void play(KeyValue key, boolean enabled)
  {
    if (!enabled)
      return;
    int effect = effect_of_key(key);
    if (effect == -1)
      return;
    try
    {
      if (_playback.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)
        _playback.playSoundEffect(effect);
    }
    catch (RuntimeException e)
    {
      // Audio services/policy must never prevent input. Do not log key data.
    }
  }

  /** Pure selection logic; -1 means no sound. Use the selected value, not the
      center label, so swiped and modified keys get the appropriate effect. */
  static int effect_of_key(KeyValue key)
  {
    if (key == null)
      return -1;
    switch (key.getKind())
    {
      case Placeholder:
      case Slider:
        return -1;
      case Editing:
        switch (key.getEditing())
        {
          case SPACE_BAR: return AudioManager.FX_KEYPRESS_SPACEBAR;
          case BACKSPACE:
          case DELETE_WORD:
          case FORWARD_DELETE_WORD: return AudioManager.FX_KEYPRESS_DELETE;
          default: break;
        }
        break;
      case Keyevent:
        switch (key.getKeyevent())
        {
          case KeyEvent.KEYCODE_SPACE: return AudioManager.FX_KEYPRESS_SPACEBAR;
          case KeyEvent.KEYCODE_DEL:
          case KeyEvent.KEYCODE_FORWARD_DEL: return AudioManager.FX_KEYPRESS_DELETE;
          case KeyEvent.KEYCODE_ENTER:
          case KeyEvent.KEYCODE_NUMPAD_ENTER: return AudioManager.FX_KEYPRESS_RETURN;
          default: break;
        }
        break;
      case Char:
        switch (key.getChar())
        {
          case ' ': return AudioManager.FX_KEYPRESS_SPACEBAR;
          case '\n': return AudioManager.FX_KEYPRESS_RETURN;
          default: break;
        }
        break;
      case Event:
        if (key.getEvent() == KeyValue.Event.ACTION)
          return AudioManager.FX_KEYPRESS_RETURN;
        break;
      default: break;
    }
    return AudioManager.FX_KEYPRESS_STANDARD;
  }

  interface Playback
  {
    int getRingerMode();
    void playSoundEffect(int effect);
  }

  private static final class NativePlayback implements Playback
  {
    private final Context _context;
    private AudioManager _audio;

    NativePlayback(Context context)
    {
      _context = context;
    }

    public int getRingerMode()
    {
      if (_audio == null)
        _audio = (AudioManager)_context.getSystemService(Context.AUDIO_SERVICE);
      return (_audio == null) ? AudioManager.RINGER_MODE_SILENT : _audio.getRingerMode();
    }

    public void playSoundEffect(int effect)
    {
      // The one-argument overload checks SOUND_EFFECTS_ENABLED and uses the
      // system effect volume. The volume overload bypasses that preference.
      _audio.playSoundEffect(effect);
    }
  }
}
