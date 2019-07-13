/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2018-2019 DaPorkchop_ and contributors
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it. Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from DaPorkchop_.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.regionmerger.anvil;

import net.daporkchop.lib.common.misc.file.PFiles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Simple test of my OverclockedRegionFile vs. Mojang's.
 *
 * @author DaPorkchop_
 */
public class AnvilTest {
    public static void main(String... args) throws IOException {
        PFiles.rmContents(new File("."));
        File file = new File("/media/daporkchop/TooMuchStuff/Misc/2b2t_org/region/r.-1.-1.mca");
        File mojangFile = new File("./mojang.mca");
        File porkFile = new File("./pork.mca");
        byte[] b = new byte[1024];

        System.out.println("Re-compressing using Mojang's RegionFile");
        {
            long time = System.currentTimeMillis();
            try (RegionFile src = new RegionFile(file);
                 RegionFile dst = new RegionFile(mojangFile)) {
                for (int x = 31; x >= 0; x--) {
                    for (int z = 31; z >= 0; z--) {
                        try (InputStream in = src.getChunkDataInputStream(x, z);
                             OutputStream out = dst.getChunkDataOutputStream(x, z)) {
                            for (int i; (i = in.read(b)) != -1; ) {
                                out.write(b, 0, i);
                            }
                        }
                    }
                }
            }
            System.out.printf("Took %dms!\n", System.currentTimeMillis() - time);
        }

        System.out.println("Re-compressing using OverclockedRegionFile");
        {
            long time = System.currentTimeMillis();
            try (OverclockedRegionFile src = new OverclockedRegionFile(file);
                 OverclockedRegionFile dst = new OverclockedRegionFile(porkFile)) {
                for (int x = 31; x >= 0; x--) {
                    for (int z = 31; z >= 0; z--) {
                        dst.writeDirect(x, z, src.readDirect(x, z));
                    }
                }
            }
            System.out.printf("Took %dms!\n", System.currentTimeMillis() - time);
        }

        System.out.println("Final checks:");
        System.out.println("  Mojang:");
        try (RegionFile src = new RegionFile(mojangFile)) {
        }
        System.out.println("  Pork:");
        try (OverclockedRegionFile src = new OverclockedRegionFile(porkFile)) {
        }
    }
}