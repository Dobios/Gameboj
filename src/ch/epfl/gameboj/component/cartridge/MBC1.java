package ch.epfl.gameboj.component.cartridge;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;

import ch.epfl.gameboj.bits.Bits;
import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.memory.Ram;
import ch.epfl.gameboj.component.memory.Rom;

import java.io.*;

public final class MBC1 implements Component {
    private static final int RAM_ENABLE = 0xA;

    private enum Mode { MODE_0, MODE_1 };

    private final Rom rom;
    private Ram ram;

    private boolean ramEnabled;
    private Mode mode;
    private int romLsb5, ramRom2;
    private final int romMask, ramMask;

    public MBC1(Rom rom, int ramSize) {
        this.rom = rom;
        this.ram = new Ram(ramSize);

        this.ramEnabled = false;
        this.mode = Mode.MODE_0;
        this.romLsb5 = 1;
        this.ramRom2 = 0;

        this.romMask = rom.size() - 1;
        this.ramMask = ramSize - 1;
    }

    public int read(int address) {
        switch (Bits.extract(checkBits16(address), 13, 3)) {
        case 0: case 1:
            return rom.read(romAddress(msb2(), 0, address));
        case 2: case 3:
            return rom.read(romAddress(ramRom2, romLsb5, address));
        case 5:
            return ramEnabled ? ram.read(ramAddress(address)) : 0xFF;
        default:
            return NO_DATA;
        }
    }

    @Override
    public void write(int address, int data) {
        checkBits8(data);
        switch (Bits.extract(checkBits16(address), 13, 3)) {
        case 0:
            ramEnabled = Bits.clip(4, data) == RAM_ENABLE;
            break;
        case 1:
            romLsb5 = Math.max(1, Bits.clip(5, data));
            break;
        case 2:
            ramRom2 = Bits.clip(2, data);
            break;
        case 3:
            mode = Bits.test(data, 0) ? Mode.MODE_1 : Mode.MODE_0;
            break;
        case 5:
            if (ramEnabled)
                ram.write(ramAddress(address), data);
            break;
        }
    }

    private int msb2() {
        switch (mode) {
        case MODE_0: return 0;
        case MODE_1: return ramRom2;
        default: throw new Error();
        }
    }

    private int romAddress(int b_20_19, int b_18_14, int b_13_0) {
        return ((b_20_19 << 19) | (b_18_14 << 14) | Bits.clip(14, b_13_0)) & romMask;
    }

    private int ramAddress(int b_12_0) {
        return ((msb2() << 13) | Bits.clip(13, b_12_0)) & ramMask;
    }

    /**
     * Saves the state of the ram onto an external file
     * @param fileName the name of the external file
     * @throws IOException if there is an error during the save
     */
    public void save(String fileName) throws IOException {
        byte[] ramData = new byte[ram.size()];
        try (OutputStream state = new FileOutputStream(fileName + ".bin")) {
            for(int i = 0; i < ram.size(); ++i) {
                ramData[i] = (byte) ram.read(i);
            }
            state.write(ramData);
        }
    }

    /**
     * Loads the state of the ram from a given file
     * @param fileName the name of the file from where it should load the data.
     * @throws IOException if there is an error during the loading
     */
    public void load(String fileName) throws IOException {
        byte[] ramData;
        try(InputStream state = new FileInputStream(fileName + ".bin")) {
            ramData = state.readAllBytes();
            for (int i = 0; i < ram.size(); ++i)
                ram.write(i, (int)ramData[i]);
        }
    }
}
