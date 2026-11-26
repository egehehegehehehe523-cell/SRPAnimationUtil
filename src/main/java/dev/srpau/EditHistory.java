package dev.srpau;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded session-local command history. Store changed tracks only, not
 * entire models/textures or native sampled animation libraries. */
final class EditHistory {
    private static final int LIMIT=64;
    private static final class Entry {
        final Runnable undo,redo;
        Entry(Runnable undo,Runnable redo){this.undo=undo;this.redo=redo;}
    }
    private final Deque<Entry> past=new ArrayDeque<>(),future=new ArrayDeque<>();
    boolean canUndo(){return !past.isEmpty();}
    boolean canRedo(){return !future.isEmpty();}
    void record(Runnable undo,Runnable redo){
        past.addLast(new Entry(undo,redo));future.clear();
        if(past.size()>LIMIT)past.removeFirst();
    }
    boolean undo(){
        if(!canUndo())return false;
        Entry e=past.removeLast();e.undo.run();future.addLast(e);return true;
    }
    boolean redo(){
        if(!canRedo())return false;
        Entry e=future.removeLast();e.redo.run();past.addLast(e);return true;
    }
}
