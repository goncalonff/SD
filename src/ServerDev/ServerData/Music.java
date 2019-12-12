package ServerDev.ServerData;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Music {
    private String title;
    private String artist;
    private Integer year;

    private Collection<String> tags;

    private int readers;
    private int downloads;

    private ReentrantLock lock_music;

    private Condition cond_writer;
    private boolean writer;


    public Music(String title, String artist, Integer year, Collection<String> tags) {
        this.title = title;
        this.artist = artist;
        this.year = year;

        this.setTags(tags);

        this.readers = 0;
        this.downloads = 0;

        this.lock_music = new ReentrantLock();

        this.cond_writer = lock_music.newCondition();
        this.writer = false;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public void setTags(Collection<String> tags) {
        this.tags = new HashSet<>(tags);
    }

    public boolean getWriter() {
        return writer;
    }

    public void swapWriterValue(){
        this.writer = ! this.writer;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public Integer getYear() {
        return year;
    }

    public Collection<String> getTags() {
        return new HashSet<>(tags);
    }

    public static String tryKey(String name, String title, String year){
        return name+year+title;
    }

    public String getKey(){
        return this.getArtist()+this.getYear()+this.getTitle();
    }

    public void lockMusic(){
        lock_music.lock();
    }

    public void unlockMusic(){
        lock_music.unlock();
    }

    public void addReader(){
        this.readers++;
    }

    public void takeReader(){
        this.readers--;
    }

    public void incrementDownloads(){
        this.downloads++;
    }

    public void awaitCondWriters() throws InterruptedException {
        while(this.writer) this.cond_writer.await();
    }
}