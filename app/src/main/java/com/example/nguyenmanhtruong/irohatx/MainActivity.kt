package com.example.nguyenmanhtruong.irohatx

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

//    static {
//        try {
//            System.loadLibrary("irohajava");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    companion object {

        init {
            System.loadLibrary("irohajava")
        }

    }

    private lateinit var irohaConnection: IrohaConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        irohaConnection = IrohaConnection(this)

        send_details.setOnClickListener {
            irohaConnection.execute(username.text.toString(),
                            details.text.toString())
                            .subscribeOn(Schedulers.computation())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe (
                                    { result ->
                                        progress_bar.visibility = View.GONE
                                        get_user_details.text = "The result is ${result}"
                                    },
                                    { error ->
                                        progress_bar.visibility = View.GONE
                                        get_user_details.text = "Error ${error.message}"
                                    }
                            )
        }
    }
}
