package ServerDev;

import Exceptions.*;
import ServerDev.ServerData.Music;
import ServerDev.ServerData.ParseFich;
import ServerDev.ServerData.User;
import Requests.Request;
import ServerDev.ServerThroughput.TransferControl;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ServerModel {
    private static final String PATH_SERVER_MUSICS = "/home/gonca/Desktop/file_share/";

    private Map<String, User> users;
    private ReentrantLock lock_users;


    public ServerModel() throws IOException {
        this.users = ParseFich.loadUsers("/home/gonca/Desktop/test_user");
        this.lock_users = new ReentrantLock();

        this.musics = ParseFich.loadMusicas("/home/gonca/Desktop/test_music");
        this.lock_musics = new ReentrantLock();
        this.transfer_control = new TransferControl();
    }

    /**
     *
     * @param user_in
     * @param pass_in
     * @throws ExceptionLogin
     */
    public void login(String user_in, String pass_in) throws ExceptionLogin {

        lock_users.lock();

        if(users.containsKey(user_in)){

            User user = users.get(user_in);

            user.lockUser();
            lock_users.unlock();

            if(user.getLogged()){

                user.unlockUser();
                throw new ExceptionLogin("Already Logged In.");

            }
            else{
                if(pass_in.equals(user.getPassword())){

                    user.setLogged(true);
                    user.unlockUser();

                }
                else{

                    user.unlockUser();
                    throw new ExceptionLogin("Wrong Password.");

                }
            }
        }
        else{

            lock_users.unlock();
            throw new ExceptionLogin("Account Does Not Exist.");

        }
    }

    /**
     *
     * @param user_logged
     * @throws ExceptionLogout
     */
    public void logout(String user_logged) throws ExceptionLogout {
        lock_users.lock();

        if(users.containsKey(user_logged) && users.get(user_logged).getLogged()){

            User user = users.get(user_logged);

            user.lockUser();
            lock_users.unlock();


            if(user.getNumCurrentTransfers() > 0)
                throw new ExceptionLogout("You Can Not Logout With Transfers Remaining.");


            user.setLogged(false);

            user.unlockUser();

        }
        else{

            lock_users.unlock();
            throw  new ExceptionLogout("You Are Not Logged In.");

        }
    }

    /**
     *
     * @param user_reg
     * @param pass_reg
     * @throws ExceptionRegister
     */
    public void register(String user_reg, String pass_reg) throws ExceptionRegister {

        lock_users.lock();

        if(users.containsKey(user_reg)){

            lock_users.unlock();
            throw new ExceptionRegister("Account Already Exists.");

        }

        users.put(user_reg,new User(user_reg,pass_reg));
        lock_users.unlock();
    }



    private Map<String, Music> musics;
    private ReentrantLock lock_musics;
    private TransferControl transfer_control;


    public void upload(String name_upload, String title_upload, String year_upload,
                       Collection<String> tags_upload, BufferedReader br) throws ExceptionUpload {
        lock_musics.lock();

        if(musics.containsKey(Music.tryKey(name_upload,title_upload,year_upload))){
            Music music = musics.get(Music.tryKey(name_upload,title_upload,year_upload));

            music.lockMusic();
            lock_musics.unlock();

            music.unlockMusic();
            throw new ExceptionUpload("This Music Already Exists, Your Account Was Added As Owner.");
        }

        Music music = new Music(name_upload,title_upload,Integer.parseInt(year_upload),tags_upload);

        music.lockMusic();

        musics.put(music.getKey(),music);
        lock_musics.unlock();

        if(!music.getWriter()) music.swapWriterValue();

        music.unlockMusic();

        try {
            transfer_control.startUpload(); // 1

            Request ur = new Request(new BufferedWriter(new FileWriter(PATH_SERVER_MUSICS+music.getKey())),br); // 2
            ur.transferRequest(); // 3

            transfer_control.endUpload();
        } catch (InterruptedException e) { // 1
            transfer_control.getLockUp().unlock();
            throw new ExceptionUpload("(Upload) Error On The Waiting List, Try Again.");
        } catch (IOException e) { // 2 & 3
            transfer_control.endUpload();
            throw new ExceptionUpload("(Upload) Error Occurred While Copying The File, Try Again.");
        } finally {
            lock_musics.lock();
            musics.remove(music.getKey());
            lock_musics.unlock();
        }

        music.lockMusic();
        music.swapWriterValue();
        music.unlockMusic();
    }




    public void download(String input, PrintWriter pw, boolean download_by_key) throws ExceptionDownload {
        if(download_by_key){
            this.downloadByKey(input,pw);
        }
        else {
            this.downloadByTitleArtistYear(input,pw);
        }
    }

    private void downloadByTitleArtistYear(String music_parameters, PrintWriter pw) throws ExceptionDownload {
        String [] parameter = music_parameters.split("«");

        if(parameter.length == 3){
            String try_key = Music.tryKey(parameter[0],parameter[1],parameter[2]);

            downloadByKey(try_key,pw);
        }
        else{
            throw new ExceptionDownload("The Parameters Were Not Correctly Filled.");
        }
    }

    private void downloadByKey(String music_key, PrintWriter pw) throws ExceptionDownload {
        lock_musics.lock();
        if(musics.containsKey(music_key)){
            Music music = musics.get(music_key);

            music.lockMusic();
            lock_musics.unlock();

            try {
                music.awaitCondWriters();
                music.addReader();
                music.unlockMusic();
            } catch (InterruptedException e) {
                music.unlockMusic();
                throw new ExceptionDownload("This Music Is Being Uploaded, Try Later.");
            }

            try {
                transfer_control.startDownload(); // 1

                Request dr = new Request(new BufferedWriter(pw), new BufferedReader(
                                                 new FileReader(PATH_SERVER_MUSICS+music_key))); // 2
                dr.transferRequest(); // 3

                transfer_control.endDownload();

            } catch (InterruptedException e) { // 1
                transfer_control.getLockDown().unlock();
                throw new ExceptionDownload("(Download) Error On The Waiting List, Try Again.");
            } catch (FileNotFoundException e) { // 2
                transfer_control.endDownload();
                throw new ExceptionDownload("(Download) File Not Found.");
            } catch (IOException e) {
                transfer_control.endDownload(); // 3
                throw new ExceptionDownload("(Download) Error Copying The File, Try Again.");
            }

            music.lockMusic();
            music.takeReader();
            music.incrementDownloads();
            music.unlockMusic();
        }
        else{
            lock_musics.unlock();
            throw new ExceptionDownload("This Key Is Invalid, Check The Spelling.");
        }
    }


    public Collection<String> searchByTags(Set<String> music_tags){
        lock_musics.lock();
        Collection<Music> musics_now =  musics.values();
        lock_musics.unlock();

        Collection<String> musics_with_tags = new ArrayList<>();

        for(Music m : musics_now){
            m.lockMusic();
            for(String tag : m.getTags())
                if(music_tags.contains(tag))
                    musics_with_tags.add(m.getKey()+" : "+m.getArtist()+" - "+m.getTitle()+" - "+m.getYear());
            m.unlockMusic();
        }

        return musics_with_tags;
    }


    /*
    public void searchByPopularity(){

    }

    public void searchByKey(String music_key){

    }

    public void searchByTitle(String music_title){

    }

    public void searchByAno(String music_year){

    }

    public void searchByArtist(String music_artist){

    }
     */
}