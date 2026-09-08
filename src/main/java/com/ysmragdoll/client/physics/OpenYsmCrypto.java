package com.ysmragdoll.client.physics;

import rip.ysm.algorithms.CityHash;
import rip.ysm.algorithms.MT19937;
import rip.ysm.algorithms.XChaCha20;
import rip.ysm.zstd.ZstdUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** OpenYSM crypto=3 文件解码的最小适配层；算法类随本模组按 MIT 许可打包。 */
final class OpenYsmCrypto {
    private static final long SEED_KEY_DERIVATION = 0xD017CBBA7B5D3581L;
    private static final long SEED_FILE_VERIFICATION = 0x9E5599DB80C67C29L;
    private static final long SEED_RES_VERIFICATION = 0xA62B1A2C43842BC3L;

    private OpenYsmCrypto() {
    }

    static byte[] decryptYsmFile(byte[] fileData) throws Exception {
        if (fileData == null || fileData.length < 72) {
            throw new IllegalArgumentException("YSM 文件过短");
        }
        int headerLength = 0;
        while (headerLength < fileData.length && fileData[headerLength] != 0) {
            headerLength++;
        }
        int tailOffset = fileData.length - 64;
        byte[] key = Arrays.copyOfRange(fileData, tailOffset, tailOffset + 32);
        byte[] iv = Arrays.copyOfRange(fileData, tailOffset + 32, tailOffset + 56);
        long expectedHash = ByteBuffer.wrap(fileData, tailOffset + 56, 8)
                .order(ByteOrder.LITTLE_ENDIAN).getLong();
        long actualHash = new CityHash().hash64WithSeed(
                Arrays.copyOf(fileData, fileData.length - 8), SEED_FILE_VERIFICATION);
        if (actualHash != expectedHash) {
            throw new IllegalArgumentException("YSM 文件完整性校验失败");
        }
        int binaryOffset = headerLength + 1;
        int crypto = ByteBuffer.wrap(fileData, binaryOffset, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (crypto != 3) {
            throw new IllegalArgumentException("不支持的 YSM crypto 版本: " + crypto);
        }
        byte[] encrypted = Arrays.copyOfRange(fileData, binaryOffset + 4, tailOffset);
        byte[] decrypted = modifiedChaChaDecrypt(encrypted, key, iv, SEED_RES_VERIFICATION);
        byte[] keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);
        byte[] xored = mt19937Xor(decrypted, keyIv, SEED_KEY_DERIVATION);
        int padding = ((xored[0] & 0xFF) | ((xored[1] & 0xFF) << 8)) & 0x3FF;
        return ZstdUtil.decompress(wash(Arrays.copyOfRange(xored, padding + 2, xored.length)));
    }

    /** OpenYSM 的 YSM Zstd block header 还原。 */
    private static byte[] wash(byte[] data) {
        if (data.length < 5) throw new IllegalArgumentException("YSM Zstd 数据过短");
        int magic = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (magic != 0xFD2FB528) throw new IllegalArgumentException("不是 Zstd 数据");
        byte frameHeader = data[4];
        data[4] = (byte) (frameHeader & 0xFB);
        int offset = 4 + frameHeaderSize(frameHeader);
        while (offset + 3 <= data.length) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;
            int last = (b0 >> 7) & 1;
            int ysmType = (b0 >> 5) & 3;
            int encodedSize = ((b0 & 0x1F) << 16) | b1 | (b2 << 8);
            int size = encodedSize ^ 0xD4E9;
            int standardType = switch (ysmType) {
                case 0 -> 2;
                case 1 -> 1;
                case 2 -> 3;
                default -> 0;
            };
            int standardHeader = last | (standardType << 1) | (size << 3);
            data[offset] = (byte) standardHeader;
            data[offset + 1] = (byte) (standardHeader >> 8);
            data[offset + 2] = (byte) (standardHeader >> 16);
            offset += 3 + (standardType == 1 ? 1 : size);
            if (last == 1) break;
        }
        return data;
    }

    private static int frameHeaderSize(byte header) {
        int dict = switch (header & 3) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            default -> 0;
        };
        boolean singleSegment = ((header >> 5) & 1) == 1;
        int content = switch ((header >> 6) & 3) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            default -> singleSegment ? 1 : 0;
        };
        return 1 + (singleSegment ? 0 : 1) + dict + content;
    }

    private static byte[] modifiedChaChaDecrypt(byte[] data, byte[] key, byte[] iv,
                                                  long seed) throws Exception {
        long keyHash = new CityHash().hash64WithSeed(concat(key, iv), seed);
        int nextSize = (int) (((keyHash & 0x3FL) | 0x40L) << 6);
        int rounds = (int) (10 * Long.remainderUnsigned(keyHash, 3) + 10);
        XChaCha20 context = new XChaCha20(key, iv, rounds);
        byte[] result = new byte[data.length];
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(nextSize, data.length - offset);
            byte[] chunk = context.processBytes(data, offset, length);
            System.arraycopy(chunk, 0, result, offset, length);
            offset += length;
            if (offset < data.length) {
                long hash = new CityHash().hash64WithSeed(chunk, seed);
                nextSize = context.updateStateYSM(hash);
            }
        }
        return result;
    }

    private static byte[] mt19937Xor(byte[] data, byte[] key, long seed) {
        long mtSeed = new CityHash().hash64WithSeed(key, seed);
        MT19937 generator = new MT19937(mtSeed);
        byte[] result = new byte[data.length];
        int offset = 0;
        while (offset < data.length) {
            long random = generator.extract_number();
            for (int i = 0; i < 8 && offset < data.length; i++, offset++) {
                result[offset] = (byte) (data[offset] ^ (byte) (random >>> (i * 8)));
            }
        }
        return result;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
