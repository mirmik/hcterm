package com.example.hcterm

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : Activity() {
    var btnSend: Button? = null
    var sendtxt: EditText? = null
    var h: Handler? = null
    val RECIEVE_MESSAGE = 1 // Статус для Handler
    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private val sb = StringBuilder()
    private var mConnectedThread: ConnectedThread? = null

    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnSend = findViewById<View>(R.id.btnSend) as Button // кнопка включения
        sendtxt = findViewById<View>(R.id.sendtxt) as EditText // для вывода текста, полученного от Arduino
        h = object : Handler() {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    RECIEVE_MESSAGE -> {
                        val readBuf = msg.obj as ByteArray
                        val strIncom = String(readBuf, 0, msg.arg1)
                        sb.append(strIncom) // формируем строку
                        val endOfLineIndex = sb.indexOf("\r\n") // определяем символы конца строки
                        if (endOfLineIndex > 0) {                                            // если встречаем конец строки,
                            val sbprint = sb.substring(0, endOfLineIndex) // то извлекаем строку
                            sb.delete(0, sb.length) // и очищаем sb
                            btnSend!!.isEnabled = true
                        }
                    }
                }
            }
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter() // получаем локальный Bluetooth адаптер
        checkBTState()
        btnSend!!.setOnClickListener {
            //btnOn!!.isEnabled = false
            mConnectedThread!!.write(sendtxt!!.getText().toString()+"\n") // Отправляем через Bluetooth цифру 1
            sendtxt!!.setText("")
            //Toast.makeText(getBaseContext(), "Включаем LED", Toast.LENGTH_SHORT).show();
        }
    }

    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "...onResume - попытка соединения...")

        // Set up a pointer to the remote node using it's address.
        val device = btAdapter!!.getRemoteDevice(address)

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
        } catch (e: IOException) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.message + ".")
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter!!.cancelDiscovery()

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Соединяемся...")
        try {
            btSocket!!.connect()
            Log.d(TAG, "...Соединение установлено и готово к передачи данных...")
        } catch (e: IOException) {
            try {
                btSocket!!.close()
            } catch (e2: IOException) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.message + ".")
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Создание Socket...")
        mConnectedThread = ConnectedThread(btSocket!!)
        mConnectedThread!!.start()
    }

    public override fun onPause() {
        super.onPause()
        Log.d(TAG, "...In onPause()...")
        try {
            btSocket!!.close()
        } catch (e2: IOException) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.message + ".")
        }
    }

    private fun checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if (btAdapter == null) {
            errorExit("Fatal Error", "Bluetooth не поддерживается")
        } else {
            if (btAdapter!!.isEnabled) {
                Log.d(TAG, "...Bluetooth включен...")
            } else {
                //Prompt user to turn on Bluetooth
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        }
    }

    private fun errorExit(title: String, message: String) {
        Toast.makeText(baseContext, "$title - $message", Toast.LENGTH_LONG).show()
        finish()
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            val buffer = ByteArray(256) // buffer store for the stream
            var bytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream!!.read(buffer) // Получаем кол-во байт и само собщение в байтовый массив "buffer"
                    h!!.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget() // Отправляем в очередь сообщений Handler
                } catch (e: IOException) {
                    Log.d("data","Exception in ConnectionThread")
                    break
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        fun write(message: String) {
            Log.d(TAG, "...Данные для отправки: $message...")
            val msgBuffer = message.toByteArray()
            try {
                mmOutStream!!.write(msgBuffer)
            } catch (e: IOException) {
                Log.d(TAG, "...Ошибка отправки данных: " + e.message + "...")
            }
        }

        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
            }
        }

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    companion object {
        private const val TAG = "bluetooth2"
        private const val REQUEST_ENABLE_BT = 1

        // SPP UUID сервиса
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // MAC-адрес Bluetooth модуля
        private const val address = "98:D3:11:FD:4C:BD"
    }
}