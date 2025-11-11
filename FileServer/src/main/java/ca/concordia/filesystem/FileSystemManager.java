package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {
            try{
                //TODO Initialize the file system
                this.disk = new RandomAccessFile(filename, "rw");
                this.disk.setLength(totalSize);
                this.inodeTable = new FEntry[MAXFILES];
                this.freeBlockList = new boolean[MAXBLOCKS];
                java.util.Arrays.fill(freeBlockList, true);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }

        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
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

        globalLock.lock();
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
        }
        finally {
            globalLock.unlock();
        }
    }
    
    // TODO: Add readFile, writeFile and other required methods,
    public void readFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public void writeFile(String fileName, String content) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }


    // Helper functions
    private int getBlockCountForSize(int sizeBytes) {
        return (sizeBytes + BLOCK_SIZE - 1) / BLOCK_SIZE;
    }

    private long calculateBlockOffset(int blockIndex) {
        return (long) blockIndex * BLOCK_SIZE;
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
        globalLock.lock();
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

            // Remove inode
            inodeTable[index] = null;
        } finally {
            globalLock.unlock();
        }
    }

    public String[] listFile() throws Exception {
        globalLock.lock();
        try {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null) {
                    names.add(inodeTable[i].getFilename());
                }
            }

            return names.toArray(new String[0]);
        } finally {
            globalLock.unlock();
        }
    }
}
