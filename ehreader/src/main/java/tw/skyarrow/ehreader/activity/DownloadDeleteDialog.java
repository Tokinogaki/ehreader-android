package tw.skyarrow.ehreader.activity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import java.io.File;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import de.greenrobot.event.EventBus;
import tw.skyarrow.ehreader.Constant;
import tw.skyarrow.ehreader.R;
import tw.skyarrow.ehreader.db.DaoMaster;
import tw.skyarrow.ehreader.db.DaoSession;
import tw.skyarrow.ehreader.db.Download;
import tw.skyarrow.ehreader.db.DownloadDao;
import tw.skyarrow.ehreader.db.Gallery;
import tw.skyarrow.ehreader.db.Photo;
import tw.skyarrow.ehreader.db.PhotoDao;
import tw.skyarrow.ehreader.event.GalleryDeleteEvent;
import tw.skyarrow.ehreader.util.UriHelper;

/**
 * Created by SkyArrow on 2014/2/3.
 */
public class DownloadDeleteDialog extends DialogFragment {
    public static final String TAG = "DownloadDeleteDialog";

    private SQLiteDatabase db;
    private DaoMaster daoMaster;
    private DaoSession daoSession;
    private PhotoDao photoDao;
    private DownloadDao downloadDao;

    private long galleryId;
    private ProgressDialog dialog;
    private Download download;
    private Gallery gallery;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        dialog = new ProgressDialog(getActivity());
        Bundle args = getArguments();
        galleryId = args.getLong("id");

        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(getActivity(), Constant.DB_NAME, null);
        db = helper.getWritableDatabase();
        daoMaster = new DaoMaster(db);
        daoSession = daoMaster.newSession();
        photoDao = daoSession.getPhotoDao();
        downloadDao = daoSession.getDownloadDao();

        download = downloadDao.load(galleryId);
        gallery = download.getGallery();

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setTitle(R.string.gallery_deleting);
        dialog.setMax(gallery.getCount());
        dialog.setProgress(0);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        new GalleryDeleteTask().execute();

        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        db.close();
    }

    private class GalleryDeleteTask extends AsyncTask<Integer, Integer, String> {
        @Override
        protected String doInBackground(Integer... integers) {
            QueryBuilder qb = photoDao.queryBuilder();
            qb.where(PhotoDao.Properties.GalleryId.eq(galleryId));
            List<Photo> photos = qb.list();

            for (Photo photo : photos) {
                File file = UriHelper.getPhotoFile(photo);

                if (file.exists()) {
                    file.delete();
                }

                photo.setDownloaded(false);
                photoDao.updateInTx(photo);
                publishProgress(1);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            dialog.incrementProgressBy(values[0]);
        }

        @Override
        protected void onPostExecute(String s) {
            File galleryFolder = UriHelper.getGalleryFolder(gallery);

            if (galleryFolder.exists()) {
                galleryFolder.delete();
            }

            downloadDao.delete(download);

            EventBus.getDefault().post(new GalleryDeleteEvent(galleryId));
            dismiss();
        }
    }
}
