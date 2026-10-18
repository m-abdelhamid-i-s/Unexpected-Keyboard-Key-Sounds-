package juloo.keyboard2;

import android.media.AudioManager;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static juloo.keyboard2.TestUtils.*;
import static org.junit.Assert.*;

public class KeypressSoundTest
{
  static class Playback implements KeypressSound.Playback
  {
    int ringer = AudioManager.RINGER_MODE_NORMAL;
    int checks = 0;
    boolean failCheck = false;
    boolean failPlay = false;
    final List<Integer> effects = new ArrayList<Integer>();

    public int getRingerMode()
    {
      checks++;
      if (failCheck) throw new SecurityException("Audio service unavailable");
      return ringer;
    }

    public void playSoundEffect(int effect)
    {
      if (failPlay) throw new IllegalStateException("Audio service unavailable");
      effects.add(effect);
    }
  }

  @Test
  public void standard_and_special_keys()
  {
    for (String name : new String[]{"a", "1", "!", "shift", "ctrl", "alt", "fn",
        "compose", "tab", "esc", "copy", "paste", "switch_numeric"})
      assertEquals(name, AudioManager.FX_KEYPRESS_STANDARD, KeypressSound.effect_of_key(key(name)));
    assertEquals(AudioManager.FX_KEYPRESS_STANDARD, KeypressSound.effect_of_key(str("hello")));
  }

  @Test
  public void space_delete_and_return_representations()
  {
    assertEffect(AudioManager.FX_KEYPRESS_SPACEBAR, key("space"), key(" "),
        keyevent(KeyEvent.KEYCODE_SPACE));
    assertEffect(AudioManager.FX_KEYPRESS_DELETE, key("backspace"), key("delete"),
        key("delete_word"), key("forward_delete_word"), keyevent(KeyEvent.KEYCODE_DEL));
    assertEffect(AudioManager.FX_KEYPRESS_RETURN, key("enter"), key("action"),
        KeyValue.makeCharKey('\n'), keyevent(KeyEvent.KEYCODE_NUMPAD_ENTER));
  }

  @Test
  public void invalid_keys_and_sliders_are_silent()
  {
    assertEffect(-1, null, new KeyValue("", KeyValue.Kind.Placeholder,
          KeyValue.Placeholder.REMOVED.ordinal(), 0), key("cursor_left"));
  }

  @Test
  public void disabled_and_invalid_keys_do_not_contact_audio_service()
  {
    Playback playback = new Playback();
    KeypressSound sound = new KeypressSound(playback);
    sound.play(key("a"), false);
    sound.play(null, true);
    sound.play(key("cursor_left"), true);
    assertEquals(0, playback.checks);
    assertTrue(playback.effects.isEmpty());
  }

  @Test
  public void enabled_and_ringer_changes_take_effect_on_next_action()
  {
    Playback playback = new Playback();
    KeypressSound sound = new KeypressSound(playback);
    sound.play(key("a"), true);
    playback.ringer = AudioManager.RINGER_MODE_SILENT;
    sound.play(key("a"), true);
    playback.ringer = AudioManager.RINGER_MODE_VIBRATE;
    sound.play(key("a"), true);
    playback.ringer = AudioManager.RINGER_MODE_NORMAL;
    sound.play(key("space"), true);
    sound.play(key("enter"), false);
    assertEquals(Arrays.asList(AudioManager.FX_KEYPRESS_STANDARD,
          AudioManager.FX_KEYPRESS_SPACEBAR), playback.effects);
  }

  @Test
  public void audio_failures_are_contained_and_later_actions_recover()
  {
    Playback playback = new Playback();
    KeypressSound sound = new KeypressSound(playback);
    playback.failCheck = true;
    sound.play(key("a"), true);
    playback.failCheck = false;
    playback.failPlay = true;
    sound.play(key("a"), true);
    playback.failPlay = false;
    sound.play(key("a"), true);
    assertEquals(Arrays.asList(AudioManager.FX_KEYPRESS_STANDARD), playback.effects);
  }

  static KeyValue keyevent(int code)
  {
    return new KeyValue("", KeyValue.Kind.Keyevent, code, 0);
  }

  static void assertEffect(int effect, KeyValue... keys)
  {
    for (KeyValue key : keys)
      assertEquals(effect, KeypressSound.effect_of_key(key));
  }
}
