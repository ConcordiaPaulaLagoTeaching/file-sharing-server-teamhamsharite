package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final RandomAccessFile disk;
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks
    private final int METADATA_SIZE = (MAXFILES * 15) + (MAXBLOCKS); //15 bytes per inode(11 name + 2 size + 2 firstBlock)


    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
            try{
                File fd = new File(filename);
                boolean newFile = !fd.exists();

                this.disk = new RandomAccessFile(filename, "rw");
                this.disk.setLength(totalSize);
                this.inodeTable = new FEntry[MAXFILES];
                this.freeBlockList = new boolean[MAXBLOCKS];
                if (newFile) {
                    //filesystem
                    Arrays.fill(freeBlockList, true);
                    saveMetadata();
                } else {
                    loadMetadata();
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
    }


    private void saveMetadata() throws Exception{
        try{
            globalLock.writeLock().lock();

            disk.seek(0);
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] == null) {
                    disk.write(new byte[11]);
                    disk.writeShort(0);
                    disk.writeShort(-1);
                } else {
                    byte[] nameBytes = new byte[11];
                    byte[] actual = inodeTable[i].getFilename().getBytes();
                    System.arraycopy(actual, 0, nameBytes, 0, Math.min(actual.length, 11));
                    disk.write(nameBytes);

                    disk.writeShort(inodeTable[i].getFilesize());
                    disk.writeShort(inodeTable[i].getFirstBlock());
                }
            }
            for (int i = 0; i < MAXBLOCKS; i++) {
                disk.writeBoolean(freeBlockList[i]);
            }

            disk.getFD().sync();
        }
        finally {
            globalLock.writeLock().unlock();
        }
    }

    private void loadMetadata() throws Exception {
        globalLock.writeLock().lock();
        try {
            disk.seek(0);
            for (int i = 0; i < MAXFILES; i++) {
                byte[] nameBytes = new byte[11];
                disk.readFully(nameBytes);

                String name = new String(nameBytes).trim();
                short size = disk.readShort();
                short startBlock = disk.readShort();
                if (!name.isEmpty()) {
                    inodeTable[i] = new FEntry(name, size, startBlock);
                } else {
                    inodeTable[i] = null;
                }
            }
            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = disk.readBoolean();
            }
        }
        finally {
            globalLock.writeLock().unlock();
        }
    }

    // Helper function to find the next free index
    private int getNextFreeInode(){
        for (int i = 0; i < MAXFILES; i++) {
            if (inodeTable[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private int findInodeByName(String fileName){
        for (int i = 0; i < MAXFILES; i++) {
            if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                return i;
            }
        }
        return -1;
    }

    public void createFile(String fileName) throws Exception {

        globalLock.writeLock().lock();
        try {
            if (fileName == null || fileName.isEmpty()){
                throw new IllegalArgumentException("Filename can't be empty!");
            }
            if (fileName.length() > 11){
                throw new IllegalArgumentException("Filename can't be more than 11 characters long!");
            }
            if (findInodeByName(fileName) != -1)
                throw new IllegalArgumentException("File already exists.");

            int freeIndex = getNextFreeInode();
            if (freeIndex == -1){
                throw new IllegalStateException("Max files reached.");
            }

            inodeTable[freeIndex] = new FEntry(fileName, (short) 0, (short) -1);
            saveMetadata();
        }
        finally {
            globalLock.writeLock().unlock();
        }
    }
    
    public byte[] readFile(String fileName) throws Exception {
        globalLock.readLock().lock();
        try {
            if (fileName == null || fileName.isEmpty())
                throw new IllegalArgumentException("Filename can't be empty!");

            int inodeIndex = findInodeByName(fileName);
            if (inodeIndex == -1)
                throw new IllegalArgumentException("File does not exist.");

            FEntry fe = inodeTable[inodeIndex];
            int fileSize = Short.toUnsignedInt(fe.getFilesize());
            int startBlock = fe.getFirstBlock();

            if (startBlock < 0)
                throw new IllegalStateException("File has no data blocks.");

            byte[] buffer = new byte[fileSize];
            disk.seek(calculateBlockOffset(startBlock));
            disk.readFully(buffer, 0, fileSize);
            return buffer;
        } finally {
            globalLock.readLock().unlock();
        }
    }


    public void writeFile(String fileName, byte[] data) throws Exception {
        globalLock.writeLock().lock();
        try {
            if (fileName == null || fileName.isEmpty())
                throw new IllegalArgumentException("Filename can't be empty!");

            if (data.length == 0)
                throw new IllegalArgumentException("Data can't be empty!");

            int inodeIndex = findInodeByName(fileName);
            if (inodeIndex == -1)
                throw new IllegalArgumentException("File does not exist.");

            int blocksNeeded = getBlockCountForSize(data.length);

            int startBlock = -1;
            for (int i = 0; i <= MAXBLOCKS - blocksNeeded; i++) {
                boolean allFree = true;
                for (int j = 0; j < blocksNeeded; j++) {
                    if (!freeBlockList[i + j]) {
                        allFree = false;
                        break;
                    }
                }
                if (allFree) {
                    startBlock = i;
                    break;
                }
            }

            if (startBlock == -1)
                throw new IllegalStateException("Not enough free space to write file.");

            updateBlockAllocation(startBlock, blocksNeeded, false);

            disk.seek(calculateBlockOffset(startBlock));
            disk.write(data);

            int remaining = blocksNeeded * BLOCK_SIZE - data.length;
            if (remaining > 0)
                disk.write(new byte[remaining]);

            inodeTable[inodeIndex] = new FEntry(fileName, (short) data.length, (short) startBlock);
            saveMetadata();

        } finally {
            globalLock.writeLock().unlock();
        }
    }


    // Helper functions
    private int getBlockCountForSize(int sizeBytes) {
        return (sizeBytes + BLOCK_SIZE - 1) / BLOCK_SIZE;
    }

    private long calculateBlockOffset(int blockIndex) {
        return METADATA_SIZE + (long) blockIndex * BLOCK_SIZE;
    }

    private void clearBlocks(int startBlock, int len) throws Exception {
        byte[] zeros = new byte[BLOCK_SIZE];
        for (int b = 0; b < len; b++) {
            disk.seek(calculateBlockOffset(startBlock + b));
            disk.write(zeros);
        }
    }

    private void updateBlockAllocation(int startBlock, int len, boolean free) {
        for (int b = 0; b < len; b++) {
            freeBlockList[startBlock + b] = free;
        }
    }

    public void deleteFile(String fileName) throws Exception {
        globalLock.writeLock().lock();
        try {
            if (fileName == null || fileName.isEmpty()){
                throw new IllegalArgumentException("Filename can't be empty!");
            }

            int index = findInodeByName(fileName);
            if (index == -1) throw new IllegalArgumentException("File does not exist.");

            FEntry fe = inodeTable[index];
            int size = fe.getFilesize() & 0xFFFF;
            int blocks = getBlockCountForSize(size);
            int start = fe.getFirstBlock();

            if (blocks > 0 && start >= 0) {
                clearBlocks(start, blocks);
                updateBlockAllocation(start, blocks, true);
            }

            inodeTable[index] = null; //remove inode
            saveMetadata();
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    public String[] listFile() throws Exception {
        globalLock.readLock().lock();
        try {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null) {
                    names.add(inodeTable[i].getFilename());
                }
            }
            return names.toArray(new String[0]);
        } finally {
            globalLock.readLock().unlock();
        }
    }
}
