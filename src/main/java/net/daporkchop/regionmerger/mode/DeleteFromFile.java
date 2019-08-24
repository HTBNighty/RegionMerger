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

package net.daporkchop.regionmerger.mode;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.NonNull;
import net.daporkchop.lib.common.function.io.IOConsumer;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.common.util.PorkUtil;
import net.daporkchop.lib.logging.Logger;
import net.daporkchop.lib.math.vector.i.Vec2i;
import net.daporkchop.regionmerger.World;
import net.daporkchop.regionmerger.option.Arguments;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static net.daporkchop.regionmerger.anvil.RegionConstants.SECTOR_BYTES;
import static net.daporkchop.regionmerger.anvil.RegionConstants.getOffsetIndex;

/**
 * @author DaPorkchop_
 */
public class DeleteFromFile implements Mode {
    protected static final OpenOption[] READ_ONLY_OPEN_OPTIONS = {StandardOpenOption.READ};
    protected static final OpenOption[] BACKUP_WRITE_OPEN_OPTIONS = {StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.TRUNCATE_EXISTING};
    protected static final OpenOption[] CHUNK_DELETE_OPEN_OPTIONS = {StandardOpenOption.WRITE};

    @Override
    public void printUsage(@NonNull Logger logger) {
    }

    @Override
    public Arguments arguments() {
        return new Arguments(false, false);
    }

    @Override
    public String name() {
        return "deletefromfile";
    }

    @Override
    public void run(@NonNull Arguments args) throws IOException {
        final World dst = new World(new File("/home/daporkchop/192.168.1.119/Minecraft/2b2t/2b2t_100k_partial/region"), false);

        logger.info("Loaded output world with %d existing regions.", dst.regions().size());

        Collection<Vec2i> missingChunks;
        try (Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(new File("/home/daporkchop/192.168.1.119/Minecraft/2b2t/scanresult-onlychunks.json"))))) {
            missingChunks = StreamSupport.stream(new JsonParser().parse(reader).getAsJsonArray().spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .map(obj -> new Vec2i(obj.get("x").getAsInt(), obj.get("z").getAsInt()))
                    .collect(Collectors.toSet());
        }
        Collection<Vec2i> missingRegions = missingChunks.stream()
                .map(pos -> new Vec2i(pos.getX() >> 5, pos.getY() >> 5))
                .distinct()
                .collect(Collectors.toSet());

        logger.info("Loaded %d missing chunk positions in %d regions.", missingChunks.size(), missingRegions.size())
                .info("Backing up regions...");

        {
            File backupPath = new File(dst.path().getAbsoluteFile().getParentFile(), "region_backup");
            PFiles.ensureDirectoryExists(backupPath);
            PFiles.rmContents(backupPath);

            missingRegions.parallelStream()
                    .map(dst::getAsFile)
                    .filter(File::exists)
                    .forEach((IOConsumer<File>) file -> {
                        int len = (int) file.length();
                        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(len);
                        try {
                            try (FileChannel channel = FileChannel.open(file.toPath(), READ_ONLY_OPEN_OPTIONS)) {
                                if (buf.writeBytes(channel, len) != len) {
                                    throw new IllegalStateException(String.format("Couldn't read %d bytes!", len));
                                }
                            }
                            try (FileChannel channel = FileChannel.open(new File(backupPath, file.getName()).toPath(), BACKUP_WRITE_OPEN_OPTIONS)) {
                                if (buf.readBytes(channel, len) != len) {
                                    throw new IllegalStateException(String.format("Couldn't write %d bytes!", len));
                                }
                            }
                        } finally {
                            buf.release();
                        }
                    });
        }

        if (false) {
            logger.info("Searching for regions containing the missing chunks...");
        } else {
            logger.info("Deleting all of the chunks from the world...");

            missingRegions.parallelStream()
                    .forEach((IOConsumer<Vec2i>) pos -> {
                        File rFile = dst.getAsFile(pos);
                        if (!rFile.exists()) {
                            return;
                        }
                        try (FileChannel channel = FileChannel.open(rFile.toPath(), CHUNK_DELETE_OPEN_OPTIONS)) {
                            MappedByteBuffer headers = channel.map(FileChannel.MapMode.READ_WRITE, 0L, SECTOR_BYTES);
                            missingChunks.stream()
                                    .filter(chunk -> (chunk.getX() >> 5) == pos.getX() && (chunk.getY() >> 5) == pos.getY())
                                    .mapToInt(chunk -> getOffsetIndex(chunk.getX(), chunk.getY()))
                                    .distinct()
                                    .forEach(index -> headers.putInt(index, 0));
                            PorkUtil.release(headers.force());
                        }
                    });

            logger.info("Done!");
        }
    }
}