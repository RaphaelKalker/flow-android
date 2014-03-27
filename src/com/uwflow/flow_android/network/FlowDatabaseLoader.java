package com.uwflow.flow_android.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import com.crashlytics.android.Crashlytics;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.uwflow.flow_android.constant.Constants;
import com.uwflow.flow_android.dao.FlowDatabaseHelper;
import com.uwflow.flow_android.db_object.*;
import com.uwflow.flow_android.util.JsonToDbUtil;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * This class fetches data from the network and populates the database
 */
public class FlowDatabaseLoader {
    private static final String TAG = FlowDatabaseLoader.class.getSimpleName();

    protected FlowDatabaseHelper flowDatabaseHelper;
    protected Context context;
    protected FlowImageLoader flowImageLoader;
    protected SharedPreferences sp;

    public FlowDatabaseLoader(Context context, FlowDatabaseHelper flowDatabaseHelper) {
        this.context = context;
        this.flowDatabaseHelper = flowDatabaseHelper;
        this.flowImageLoader = new FlowImageLoader(context);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void loadOrReloadProfileData(ResultCollectorCallback callback) {
        final FlowResultCollector resultCollector = new FlowResultCollector(5, callback);
        reloadUserMe(0, resultCollector);
        reloadProfileCourses(1, resultCollector);
        reloadProfileExams(2, resultCollector);
        reloadProfileFriends(3, resultCollector);
        reloadProfileSchedule(4, resultCollector);
        resultCollector.startTimer();
    }

    public void reloadUserMe(final int index, final FlowResultCollector flowResultCollector) {
        FlowApiRequests.getUser(new FlowApiRequestCallbackAdapter() {
            @Override
            public void onSuccess(JSONObject response) {
                new AsyncTask<JSONObject, Void, Object>() {
                    @Override
                    protected Void doInBackground(JSONObject... jsonObjects) {
                        try {
                            Dao<User, String> userDao = flowDatabaseHelper.getUserDao();
                            final User user = JsonToDbUtil.getUserMe(jsonObjects[0]);
                            if (user != null && user.getProfilePicUrls() != null)
                                flowImageLoader.preloadImage(user.getProfilePicUrls().getLarge());
                            if (user != null) {
                                SharedPreferences.Editor editor = sp.edit();
                                editor.putString(Constants.PROFILE_ID_KEY, user.getId());
                                editor.commit();
                                userDao.createOrUpdate(user);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                            Crashlytics.logException(e);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object obj) {
                        handleCallback(index, flowResultCollector);
                    }
                }.execute(response);
            }

            @Override
            public void onFailure(String error) {
                handleCallback(index, flowResultCollector);
            }
        });
    }

    public void updateOrCreateUserScheduleImage(ScheduleImage scheduleImage) {
        if (scheduleImage == null || scheduleImage.getImage() == null) {
            return;
        }
        if (scheduleImage.getId() == null) {
            String id = sp.getString(Constants.PROFILE_ID_KEY, null);
            if (id == null)
                return;
            else
                scheduleImage.setId(id);
        }
        new AsyncTask<ScheduleImage, Void, Void>() {
            @Override
            protected Void doInBackground(ScheduleImage... scheduleImages) {
                try {
                    Dao<ScheduleImage, String> userSchduleImageDao = flowDatabaseHelper.getUserSchduleImageDao();

                    userSchduleImageDao.createOrUpdate(scheduleImages[0]);
                } catch (SQLException e) {
                    e.printStackTrace();
                    Crashlytics.logException(e);
                }
                return null;
            }
        }.execute(scheduleImage);
    }


    public void reloadProfileFriends(final int index, final FlowResultCollector flowResultCollector) {
        FlowApiRequests.getUserFriends(new FlowApiRequestCallbackAdapter() {
            @Override
            public void onSuccess(JSONObject response) {
                new AsyncTask<JSONObject, Void, Object>() {
                    @Override
                    protected Void doInBackground(JSONObject... jsonObjects) {
                        final UserFriends userFriends = JsonToDbUtil.getUserFriends(jsonObjects[0]);
                        try {
                            final Dao<User, String> userDao = flowDatabaseHelper.getUserDao();
                            userDao.callBatchTasks(new Callable<Object>() {
                                @Override
                                public Void call() throws Exception {
                                    for (User u : userFriends.getFriends()) {
                                        userDao.createOrUpdate(u);
                                    }
                                    return null;
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            Crashlytics.logException(e);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object obj) {
                        handleCallback(index, flowResultCollector);
                    }
                }.execute(response);
            }

            @Override
            public void onFailure(String error) {
                handleCallback(index, flowResultCollector);
            }
        });
    }

    public void reloadProfileSchedule(final int index, final FlowResultCollector flowResultCollector) {
        FlowApiRequests.getUserSchedule(new FlowApiRequestCallbackAdapter() {
            @Override
            public void onSuccess(JSONObject response) {
                new AsyncTask<JSONObject, Void, Object>() {
                    @Override
                    protected Void doInBackground(JSONObject... jsonObjects) {
                        final ScheduleCourses scheduleCourses = JsonToDbUtil.getUserSchedule(jsonObjects[0]);
                        try {
                            final Dao<ScheduleCourse, String> userCourseSchedule = flowDatabaseHelper.getUserScheduleCourseDao();
                            userCourseSchedule.callBatchTasks(new Callable<Object>() {
                                @Override
                                public Void call() throws Exception {
                                    for (ScheduleCourse sc : scheduleCourses.getScheduleCourses()) {
                                        sc.setScheduleUrl(scheduleCourses.getScreenshotUrl());
                                        userCourseSchedule.createOrUpdate(sc);
                                    }
                                    return null;
                                }
                            });
                        } catch (SQLException e) {
                            e.printStackTrace();
                            Crashlytics.logException(e);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Crashlytics.logException(e);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object obj) {
                        handleCallback(index, flowResultCollector);
                    }
                }.execute(response);
            }

            @Override
            public void onFailure(String error) {
                handleCallback(index, flowResultCollector);
            }
        });
    }

    public void reloadProfileExams(final int index, final FlowResultCollector flowResultCollector) {
        FlowApiRequests.getUserExams(new FlowApiRequestCallbackAdapter() {

            @Override
            public void onSuccess(JSONObject response) {
                new AsyncTask<JSONObject, Void, Object>() {
                    @Override
                    protected Void doInBackground(JSONObject... jsonObjects) {
                        final Exams userExams = JsonToDbUtil.getUserExams(jsonObjects[0]);
                        try {
                            final Dao<Exam, Integer> userExamDao = flowDatabaseHelper.getUserExamDao();
                            userExamDao.callBatchTasks(new Callable<Object>() {
                                @Override
                                public Void call() throws Exception {
                                    for (Exam exam : userExams.getExams()) {
                                        userExamDao.createOrUpdate(exam);
                                    }
                                    return null;
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            Crashlytics.logException(e);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object obj) {
                        handleCallback(index, flowResultCollector);
                    }
                }.execute(response);
            }

            @Override
            public void onFailure(String error) {
                handleCallback(index, flowResultCollector);
            }
        });
    }

    public void reloadProfileCourses(final int index, final FlowResultCollector flowResultCollector) {
        FlowApiRequests.getUserCourses(new FlowApiRequestCallbackAdapter() {
            @Override
            public void onSuccess(JSONObject response) {
                new AsyncTask<JSONObject, Void, Object>() {
                    @Override
                    protected Void doInBackground(JSONObject... jsonObjects) {
                        final UserCourseDetail userCourses = JsonToDbUtil.getUserCourseDetail(jsonObjects[0]);
                        try {
                            final Dao<Course, String> courseDao = flowDatabaseHelper.getUserCourseDao();
                            courseDao.callBatchTasks(new Callable<Object>() {
                                @Override
                                public Void call() throws Exception {
                                    for (Course c : userCourses.getCourses()) {
                                        courseDao.createOrUpdate(c);
                                    }
                                    return null;
                                }
                            });

                            final Dao<UserCourse, String> userCourseExtraDao =
                                    flowDatabaseHelper.getUserCourseExtraDao();
                            userCourseExtraDao.callBatchTasks(new Callable<Object>() {
                                @Override
                                public Void call() throws Exception {
                                    for (UserCourse c : userCourses.getUserCourses()) {
                                        userCourseExtraDao.createOrUpdate(c);
                                    }
                                    return null;
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            Crashlytics.logException(e);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object obj) {
                        handleCallback(index, flowResultCollector);
                    }
                }.execute(response);
            }

            @Override
            public void onFailure(String error) {
                handleCallback(index, flowResultCollector);
            }
        });
    }


    public void queryUserScheduleImage(String id, final FlowDatabaseImageCallback callback) {
        new AsyncTask<String, Void, ScheduleImage>() {

            @Override
            protected ScheduleImage doInBackground(String... strings) {
                try {
                    String arg = strings[0];
                    if (arg == null) {
                        arg = sp.getString(Constants.PROFILE_ID_KEY, null);
                        if (arg == null) return null;
                    }
                    Dao<ScheduleImage, String> scheduleImageStringDao = flowDatabaseHelper.getUserSchduleImageDao();
                    QueryBuilder<ScheduleImage, String> queryBuilder = scheduleImageStringDao.queryBuilder();
                    queryBuilder.where().eq("id", arg);
                    List<ScheduleImage> images = scheduleImageStringDao.query(queryBuilder.prepare());
                    if (!images.isEmpty())
                        return images.get(0);
                } catch (Exception e) {
                    Crashlytics.logException(e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(ScheduleImage result) {
                callback.onScheduleImageLoaded(result);
            }
        }.execute(id);
    }


    protected void handleCallback(int index, FlowResultCollector flowResultCollector) {
        if (flowResultCollector != null) {
            flowResultCollector.setState(index, true);
        }
    }
}
