package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class ThemeFontsTest {

    private static File dir(String name) throws Exception {
        return Files.createTempDirectory(name).toFile();
    }

    @Test
    public void keepsAByteForByteCopyUnderTheSameNames() throws Exception {
        File pushed = dir("pushed"), kept = new File(dir("app"), ThemeFonts.DIR);
        byte[] regular = {1, 2, 3}, bold = {4, 5, 6, 7};
        Files.write(new File(pushed, ThemeFonts.THMANYAH[0]).toPath(), regular);
        Files.write(new File(pushed, ThemeFonts.THMANYAH[1]).toPath(), bold);
        ThemeFonts.sync(pushed, kept, ThemeFonts.THMANYAH);
        assertArrayEquals(regular, Files.readAllBytes(new File(kept, ThemeFonts.THMANYAH[0]).toPath()));
        assertArrayEquals(bold, Files.readAllBytes(new File(kept, ThemeFonts.THMANYAH[1]).toPath()));
        assertEquals(Arrays.asList(ThemeFonts.THMANYAH[1], ThemeFonts.THMANYAH[0]),
                Arrays.asList(kept.list()).stream().sorted().collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void unmountedStorageKeepsTheLastCopyAndARemovedFontGoes() throws Exception {
        File pushed = dir("pushed"), kept = new File(dir("app"), ThemeFonts.DIR);
        Files.write(new File(pushed, ThemeFonts.THMANYAH[0]).toPath(), new byte[]{1});
        Files.write(new File(pushed, ThemeFonts.THMANYAH[1]).toPath(), new byte[]{2});
        ThemeFonts.sync(pushed, kept, ThemeFonts.THMANYAH);
        // Storage not mounted yet at boot: nothing changes.
        ThemeFonts.sync(new File(pushed, "missing"), kept, ThemeFonts.THMANYAH);
        ThemeFonts.sync(null, kept, ThemeFonts.THMANYAH);
        assertTrue(ThemeFonts.usable(new File(kept, ThemeFonts.THMANYAH[0])));
        // theme.sh removed the font (another theme): the copy goes too, and Home falls back to IBM Plex.
        assertTrue(new File(pushed, ThemeFonts.THMANYAH[0]).delete());
        ThemeFonts.sync(pushed, kept, ThemeFonts.THMANYAH);
        assertFalse(new File(kept, ThemeFonts.THMANYAH[0]).exists());
        assertTrue(new File(kept, ThemeFonts.THMANYAH[1]).exists());
    }

    @Test
    public void theThemeProviderTakesTheTwoFontNamesOnly() {
        assertEquals("fonts/thmanyahsans-Regular.otf", ThemeFiles.path(Arrays.asList("fonts", "thmanyahsans-Regular.otf")));
        assertEquals("fonts/thmanyahsans-Bold.otf", ThemeFiles.path(Arrays.asList("fonts", "thmanyahsans-Bold.otf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "other.otf")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts", "..", "x")));
        assertNull(ThemeFiles.path(Arrays.asList("fonts")));
    }
}
