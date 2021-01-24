package ru.magzyumov.contentmanager


interface Constants {

    /**
     * Request codes
     */
    interface RequestCode {
        companion object {
            const val PERMISSION_REQUEST_CODE = 9900
            const val CONTENT_PICKER = 1001
            const val CONTENT_TAKE = 1002
            const val CONTENT_VIDEO = 1003
        }
    }

    /**
     * For save and restore instance state
     */
    interface StatesCode {
        companion object {
            const val DATE_CAMERA_INTENT_STARTED_STATE = "date_camera_intent_started_state"
            const val CAMERA_PIC_URI_STATE = "camera_pic_uri_state"
            const val PHOTO_URI_STATE = "photo_uri_state"
            const val ROTATE_X_DEGREES_STATE = "rotate_x_degrees_state"
            const val SAVED_TASK_STATE = "saved_task_state"
            const val TARGET_FILE_STATE = "target_file_state"
            const val SAVED_CONTENT_STATE = "saved_content_state"
        }
    }

    /**
     * File name date format
     */
    interface StringPattern {
        companion object {
            const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZ"
            const val UTC_TIME_ZONE = "UTC"
            const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
        }
    }

}