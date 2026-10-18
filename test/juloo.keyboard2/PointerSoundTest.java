package juloo.keyboard2;

import android.media.AudioManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static juloo.keyboard2.TestUtils.*;
import static org.junit.Assert.*;

/** Real Pointers/KeyModifier/Config with a deterministic JVM repeat scheduler.
    The recorder plays through KeypressSound, as Keyboard2View does. */
public class PointerSoundTest implements Pointers.IPointerEventHandler
{
  Config config;
  Pointers pointers;
  TestUtils.Scheduler scheduler;
  Config previousConfig;
  KeypressSoundTest.Playback playback = new KeypressSoundTest.Playback();
  KeypressSound sound = new KeypressSound(playback, Runnable::run);
  List<KeyValue> activated = new ArrayList<KeyValue>();
  List<KeyValue> emitted = new ArrayList<KeyValue>();
  int downs = 0;
  int flags = 0;

  @Before
  public void setup() throws Exception
  {
    config = new ConfigTest().create();
    config.sound_on_keypress = true;
    config.swipe_dist_px = 20.f;
    config.slide_step_px = 10.f;
    // Gesture/KeyModifier read the global config. Restore it after each test.
    Field global = Config.class.getDeclaredField("_globalConfig");
    global.setAccessible(true);
    previousConfig = (Config)global.get(null);
    global.set(null, config);
    pointers = new Pointers(this, config);
    scheduler = TestUtils.scheduler;
  }

  @After
  public void teardown() throws Exception
  {
    if (pointers != null) pointers.clear();
    Field global = Config.class.getDeclaredField("_globalConfig");
    global.setAccessible(true);
    global.set(null, previousConfig);
  }

  @Test
  public void tap_plays_immediately_without_release_or_duplicate_up_sound()
  {
    down(1, layout("a", "1"));
    assertEquals(1, downs);
    assertEquals(1, playback.effects.size());
    assertTrue(emitted.isEmpty());
    pointers.onTouchUp(1);
    pointers.onTouchUp(1);
    assertEquals(Arrays.asList(key("a")), emitted);
    assertEquals(emitted, activated);
    assertEquals(Arrays.asList(AudioManager.FX_KEYPRESS_STANDARD), playback.effects);
  }

  @Test
  public void swipe_keeps_initial_effect_without_selection_or_release_sound()
  {
    down(1, layout("a", "backspace"));
    pointers.onTouchMove(50, 50, 1); // top-left corner
    pointers.onTouchMove(40, 40, 1);
    assertEquals(2, downs);
    assertEquals(1, playback.effects.size());
    pointers.onTouchUp(1);
    assertEquals(Arrays.asList(key("backspace")), emitted);
    assertEquals(Arrays.asList(AudioManager.FX_KEYPRESS_STANDARD), playback.effects);
  }

  @Test
  public void cancelled_swipe_adds_no_sound_and_unknown_pointer_is_silent()
  {
    pointers.onTouchUp(99);
    pointers.onTouchMove(50, 50, 99);
    assertTrue(playback.effects.isEmpty());
    down(1, layout("a", "1"));
    pointers.onTouchMove(50, 50, 1);
    pointers.onTouchCancel();
    pointers.onTouchUp(1);
    assertFalse(scheduler.fireNext());
    assertEquals(Arrays.asList(key("a")), activated);
    assertTrue(emitted.isEmpty());
    assertEquals(1, playback.effects.size());
  }

  @Test
  public void removed_center_is_silent_and_reset_adds_no_sound()
  {
    down(1, layout(null, null));
    pointers.onTouchUp(1);
    assertTrue(playback.effects.isEmpty());
    down(2, layout("a", "1"));
    pointers.clear();
    pointers.onTouchUp(2);
    assertFalse(scheduler.fireNext());
    assertEquals(1, playback.effects.size());
  }

  @Test
  public void modifier_contacts_play_once_without_latch_unlatch_or_lock_duplicates()
  {
    for (String name : new String[]{"shift", "ctrl", "alt"})
    {
      pointers.clear();
      KeyboardData.Key modifier = layout(name, null);
      int before = playback.effects.size();
      down(1, modifier);
      assertEquals(before + 1, playback.effects.size());
      pointers.onTouchUp(1); // latch
      assertEquals(before + 1, playback.effects.size());
      down(2, modifier);
      pointers.onTouchUp(2); // unlatch
      assertEquals(before + 2, playback.effects.size());
      down(3, modifier);
      assertTrue(scheduler.fireNext()); // long press locks, no extra sound
      assertEquals(before + 3, playback.effects.size());
      pointers.onTouchUp(3);
      assertEquals(before + 3, playback.effects.size());
    }
  }

  @Test
  public void double_tap_lock_and_automatic_modifier_updates_do_not_duplicate_sound()
  {
    config.double_tap_lock_shift = true;
    KeyboardData.Key shift = layout("shift", null);
    down(1, shift);
    pointers.onTouchUp(1);
    down(2, shift);
    pointers.onTouchUp(2);
    assertEquals(2, playback.effects.size());
    assertTrue((pointers.getKeyFlags(key("shift")) & Pointers.FLAG_P_LOCKED) != 0);
    pointers.clear();
    pointers.set_fake_pointer_state(shift, key("shift"), true, false);
    pointers.set_fake_pointer_state(shift, key("shift"), false, false);
    assertEquals(2, playback.effects.size());
  }

  @Test
  public void modifier_swipe_and_multitouch_use_independent_activations()
  {
    down(1, layout("a", "ctrl"));
    pointers.onTouchMove(50, 50, 1);
    assertEquals(1, playback.effects.size());
    down(2, layout("b", null));
    pointers.onTouchUp(2);
    pointers.onTouchUp(1);
    assertEquals(2, activated.size());
    assertEquals(key("a"), activated.get(0));
    assertEquals(2, playback.effects.size());
  }

  @Test
  public void overlapping_keys_and_cancellation_do_not_suppress_other_actions()
  {
    down(1, layout("a", null));
    down(2, layout("b", null));
    pointers.onTouchUp(2);
    pointers.onTouchUp(1);
    assertEquals(Arrays.asList(key("a"), key("b")), activated);
    down(3, layout("c", null));
    down(4, layout("d", null));
    pointers.onTouchCancel();
    assertFalse(scheduler.fireNext());
    assertEquals(4, playback.effects.size());
  }

  @Test
  public void repeat_plays_after_contact_without_extra_release_sound()
  {
    for (String name : new String[]{"a", "backspace"})
    {
      activated.clear();
      emitted.clear();
      playback.effects.clear();
      down(1, layout(name, null));
      assertTrue(scheduler.fireNext());
      assertTrue(scheduler.fireNext());
      assertTrue(scheduler.fireNext());
      assertEquals(4, playback.effects.size());
      pointers.onTouchUp(1); // Existing behavior emits one more key here.
      assertEquals(4, emitted.size());
      assertEquals(emitted, activated);
      assertEquals(4, playback.effects.size());
      assertFalse(scheduler.fireNext());
    }
  }

  @Test
  public void cancel_after_repeat_keeps_prior_feedback_but_adds_none()
  {
    down(1, layout("backspace", null));
    scheduler.fireNext();
    pointers.onTouchCancel();
    pointers.onTouchUp(1);
    assertFalse(scheduler.fireNext());
    assertEquals(1, emitted.size());
    assertEquals(2, playback.effects.size());
  }

  @Test
  public void repeat_disabled_and_long_press_alternative()
  {
    config.keyrepeat_enabled = false;
    down(1, layout("a", null));
    scheduler.fireNext();
    assertEquals(1, playback.effects.size());
    pointers.onTouchUp(1);
    assertEquals(1, playback.effects.size());
    playback.effects.clear();
    activated.clear();
    down(2, layout("change_method_prev", null));
    scheduler.fireNext();
    assertEquals(1, playback.effects.size());
    pointers.onTouchUp(2);
    assertEquals(Arrays.asList(KeyValue.CHANGE_METHOD_PREV), activated);
    assertEquals(1, playback.effects.size());
  }

  @Test
  public void roundtrip_does_not_add_sound_after_initial_contact()
  {
    down(1, layout("a", "1"));
    pointers.onTouchMove(50, 50, 1);
    pointers.onTouchMove(100, 100, 1);
    assertEquals(1, playback.effects.size());
    pointers.onTouchUp(1);
    assertEquals(1, activated.size());
    assertEquals(Arrays.asList(key("a")), activated);
    assertEquals(1, playback.effects.size());
  }

  @Test
  public void cursor_sliding_adds_no_sound_after_initial_space_contact()
  {
    down(1, layout("space", "cursor_left"));
    pointers.onTouchMove(50, 50, 1);
    down(2, layout("a", null)); // ignored while sliding
    pointers.onTouchMove(-50, 50, 1);
    pointers.onTouchUp(1);
    assertTrue(downs >= 2);
    assertFalse(emitted.isEmpty());
    assertEquals(Arrays.asList(key("space")), activated);
    assertEquals(Arrays.asList(AudioManager.FX_KEYPRESS_SPACEBAR), playback.effects);
  }

  @Test
  public void disabled_sound_preserves_input_and_vibration_callbacks()
  {
    config.sound_on_keypress = false;
    down(1, layout("a", "1"));
    pointers.onTouchMove(50, 50, 1);
    pointers.onTouchUp(1);
    assertEquals(2, downs);
    assertEquals(1, emitted.size());
    assertEquals(1, activated.size());
    assertTrue(playback.effects.isEmpty());
    config.sound_on_keypress = true;
    down(2, layout("a", null));
    pointers.onTouchUp(2);
    assertEquals(1, playback.effects.size());
  }

  void down(int id, KeyboardData.Key key)
  {
    pointers.onTouchDown(100, 100, id, key);
  }

  @Test
  public void swipe_repeat_uses_new_selection_but_release_is_silent()
  {
    down(1, layout("a", "backspace"));
    pointers.onTouchMove(50, 50, 1);
    scheduler.fireNext();
    pointers.onTouchUp(1);
    assertEquals(Arrays.asList(AudioManager.FX_KEYPRESS_STANDARD,
          AudioManager.FX_KEYPRESS_DELETE), playback.effects);
    assertEquals(Arrays.asList(key("backspace"), key("backspace")), emitted);
  }

  static KeyboardData.Key layout(String center, String corner)
  {
    KeyValue[] keys = new KeyValue[9];
    keys[0] = center == null ? null : key(center);
    keys[1] = corner == null ? null : key(corner);
    return new KeyboardData.Key(keys, null, 0, 1.f, 0.f, null, KeyboardData.Key.Role.Normal);
  }

  public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods) { return KeyModifier.modify(k, mods); }
  public void onPointerDown(KeyValue k, boolean swipe) { downs++; }
  public void onPointerFlagsChanged(boolean vibrate) { flags++; }
  public void onPointerUp(KeyValue k, Pointers.Modifiers mods) { emitted.add(k); }
  public void onPointerHold(KeyValue k, Pointers.Modifiers mods) { emitted.add(k); }
  public void onPointerFeedback(KeyValue k)
  {
    activated.add(k);
    sound.play(k, config.sound_on_keypress, config.sound_volume);
  }
}
