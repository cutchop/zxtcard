package cn.whzxt.android;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Timer;
import java.util.TimerTask;

import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

public class ZxtCardActivity extends Activity implements OnInitListener {
	private EditText txtCardNo, txtIDCard, txtName;
	private RadioButton radStudent, radCoach;
	private Button btnWriteCard;
	private Timer _timerSecond;
	private int _type = 0; // 0,S50;1,S70
	private String _cardUID, _cardType, _cardNo, _cardIDCard, _cardName;
	private int _cardYE = 0, _cardXS = 0, _cardLC = 0;
	private int[] _address;
	private int[] _context;
	private int[] _rfidKey;
	private int _rfidKind;
	private final int NOCARD = 0x00;
	private final int FINGER1 = 0x01;
	private final int FINGER2 = 0x02;
	private final int WRITE = 0x03;
	// TTS
	private TextToSpeech mTts;
	private static final int REQ_TTS_STATUS_CHECK = 0;
	// 指纹
	private int nRet = 0;
	private static final int _fingerAddress = 0xffffffff;// 默认的地址
	private lytfingerprint _fingerprint;
	private String _fingertmppath = "/data/local/tmp/fingerprintdata";

	private char PS_OK = 0x00;
	private char PS_NO_FINGER = 0x02;

	private byte[] pTemplet = new byte[1024];
	private int iTempletLength;

	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			// speak("欢迎使用,中信通智能驾培终端");
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQ_TTS_STATUS_CHECK) {
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				mTts = new TextToSpeech(this, this);
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		radStudent = (RadioButton) findViewById(R.id.radStudent);
		radCoach = (RadioButton) findViewById(R.id.radCoach);
		txtCardNo = (EditText) findViewById(R.id.txtCardNo);
		txtIDCard = (EditText) findViewById(R.id.txtIDCard);
		txtName = (EditText) findViewById(R.id.txtName);
		btnWriteCard = (Button) findViewById(R.id.btnWriteCard);

		_timerSecond = new Timer();
		_rfidKey = new int[] { 255, 255, 255, 255, 255, 255 };
		_rfidKind = 1;
		_address = new int[2];

		// 制卡
		btnWriteCard.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (txtCardNo.getText().length() != 8) {
					toashShow("卡号错误");
					return;
				}
				if (txtIDCard.getText().length() != 18) {
					toashShow("身份证号码错误");
					return;
				}
				if (txtName.getText().length() == 0) {
					toashShow("请填写姓名");
					return;
				}
				_cardUID = Native.read_A();
				if (_cardUID.charAt(16) == '1') {
					_type = 0;
				} else if (_cardUID.charAt(16) == '2') {
					_type = 1;
				} else {
					toashShow("请插卡");
					return;
				}
				_cardNo = txtCardNo.getText().toString();
				_cardIDCard = txtIDCard.getText().toString();
				_cardName = txtName.getText().toString();
				writecard();
			}
		});

		// TTS
		Intent checkIntent = new Intent();
		try {
			checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
			startActivityForResult(checkIntent, REQ_TTS_STATUS_CHECK);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!Native.open_fm1702()) {
			speak("读卡器打开失败");
			AlertDialog alertDialog = new AlertDialog.Builder(ZxtCardActivity.this).setTitle("读卡器打开失败").setIcon(android.R.drawable.ic_menu_help).setPositiveButton("确定", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					ZxtCardActivity.this.finish();
				}
			}).create();
			alertDialog.show();
			return;
		}
		_fingerprint = new lytfingerprint();
		lytfingerprint.Open();
		if (_fingerprint.PSOpenDevice(1, 0, 57600 / 9600, 2) != 1) {
			speak("指纹模块打开失败");
			AlertDialog alertDialog = new AlertDialog.Builder(ZxtCardActivity.this).setTitle("指纹模块打开失败").setIcon(android.R.drawable.ic_menu_help).setPositiveButton("确定", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					ZxtCardActivity.this.finish();
				}
			}).create();
			alertDialog.show();
			return;
		}
	}

	private void writecard() {
		_address[0] = 1;
		_address[1] = 1;
		// 类型：教练卡、学员卡
		_context[0] = 1;
		if (radCoach.isChecked()) {
			_context[0] = 2;
		}
		// 卡号
		for (int i = 0; i < _cardNo.length(); i++) {
			_context[i + 1] = _cardNo.charAt(i);
		}
		// 余额
		String data = Integer.toHexString(_cardYE);
		while (data.length() < 4) {
			data = "0" + data;
		}
		_context[9] = Integer.parseInt(data.substring(0, 2), 16);
		_context[10] = Integer.parseInt(data.substring(2), 16);
		// 累计学时
		data = Integer.toHexString(_cardXS);
		while (data.length() < 4) {
			data = "0" + data;
		}
		_context[11] = Integer.parseInt(data.substring(0, 2), 16);
		_context[12] = Integer.parseInt(data.substring(2), 16);
		// 累计里程
		data = Integer.toHexString(_cardLC);
		while (data.length() < 6) {
			data = "0" + data;
		}
		_context[13] = Integer.parseInt(data.substring(0, 2), 16);
		_context[14] = Integer.parseInt(data.substring(2, 2), 16);
		_context[15] = Integer.parseInt(data.substring(4), 16);
		// 身份证号
		for (int i = 0; i < _cardIDCard.length(); i++) {
			_context[i + 16] = _cardIDCard.charAt(i);
		}
		// 姓名
		for (int i = 0; i < _cardName.length(); i++) {
			try {
				int j = bytes2int(String.valueOf(_cardName.charAt(i)).getBytes("GB2312"));
				data = Integer.toHexString(j);
				while (data.length() < 4) {
					data = "0" + data;
				}
				_context[34 + i * 2] = Integer.parseInt(data.substring(0, 2), 16);
				_context[35 + i * 2] = Integer.parseInt(data.substring(2), 16);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		showDialog(WRITE);
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected Integer doInBackground(Void... args) {
				return Native.write_card(_address, _rfidKey, _rfidKind, _context);
			}

			@Override
			protected void onPostExecute(Integer result) {
				if (result == 1) {
					dismissDialog(WRITE);
					if (_type == 1) {
						toashShow("请按右手拇指..");
						showDialog(FINGER1);
						recordfinger1();
					} else {
						toashShow("制卡成功");
					}
				} else {
					toashShow("写卡失败");
				}
			}
		}.execute();
	}

	private void recordfinger1() {
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected Integer doInBackground(Void... args) {
				_address[0] = 2;
				_address[1] = 11;
				if (fingertocontext() == PS_OK) {
					return Native.write_card(_address, _rfidKey, _rfidKind, _context);
				} else {
					return 0xff;
				}
			}

			@Override
			protected void onPostExecute(Integer result) {
				if (result == 1) {
					dismissDialog(FINGER1);
					toashShow("请按左手拇指..");
					showDialog(FINGER2);
					recordfinger2();
				} else {
					toashShow("写指纹失败");
				}
			}
		}.execute();
	}

	private void recordfinger2() {
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected Integer doInBackground(Void... args) {
				_address[0] = 13;
				_address[1] = 11;
				if (fingertocontext() == PS_OK) {
					return Native.write_card(_address, _rfidKey, _rfidKind, _context);
				} else {
					return 0xff;
				}
			}

			@Override
			protected void onPostExecute(Integer result) {
				if (result == 1) {
					dismissDialog(FINGER2);
					toashShow("制卡成功");
				} else {
					toashShow("写指纹失败");
				}
			}
		}.execute();
	}

	private int fingertocontext() {
		// 1，检测手指并录取图像
		while ((nRet = _fingerprint.PSGetImage(_fingerAddress)) == 2) {
			SystemClock.sleep(30);
		}
		SystemClock.sleep(10);
		// 2，根据原始图像生成指纹特征
		if ((nRet = _fingerprint.PSGenChar(_fingerAddress, 0x01)) != PS_OK) {
			while ((nRet = _fingerprint.PSGetImage(_fingerAddress)) == PS_NO_FINGER) {
				SystemClock.sleep(30);
			}
			if ((nRet = _fingerprint.PSGenChar(_fingerAddress, 0x01)) != PS_OK) {
				return nRet;
			}
		}
		SystemClock.sleep(1000);
		// 3，再一次检测手指并录取图像
		// 4，根据原始图像生成指纹特征
		while ((nRet = _fingerprint.PSGetImage(_fingerAddress)) == PS_NO_FINGER) {
			SystemClock.sleep(30);
		}
		SystemClock.sleep(30);
		if ((nRet = _fingerprint.PSGenChar(_fingerAddress, 0x02)) != PS_OK) {
			while ((nRet = _fingerprint.PSGetImage(_fingerAddress)) == PS_NO_FINGER) {
				SystemClock.sleep(30);
			}
			if ((nRet = _fingerprint.PSGenChar(_fingerAddress, 0x02)) != PS_OK) {
				return nRet;
			}
		}
		SystemClock.sleep(100);
		// 5，合成模版
		nRet = _fingerprint.PSRegModule(_fingerAddress);
		if (nRet != PS_OK) {
			return nRet;
		}
		// 6，存储到固定的某个page(0~256)
		if (_fingerprint.PSStoreChar(_fingerAddress, 0x01, 0) != PS_OK)// 覆盖
		{
			return nRet;
		}
		// 特征码写到文件
		if ((nRet = _fingerprint.PSLoadChar(_fingerAddress, 0x01, 0)) != PS_OK) {
			return nRet;
		}
		SystemClock.sleep(1000);
		if ((nRet = _fingerprint.PSUpChar(_fingerAddress, 0x01, pTemplet, iTempletLength, _fingertmppath)) != PS_OK) {
			return nRet;
		}
		
		byte[] bs = new byte[512];
		try {
			FileInputStream fi = new FileInputStream(_fingertmppath);
			fi.read(bs, 0, bs.length);			
		} catch (FileNotFoundException e) {
			return 0xff;
		} catch (IOException e) {
			return 0xff;
		}
		for (int i = 0; i < bs.length; i++) {
			_context[i] = bs[i];
		}
		return PS_OK;
	}

	private void toashShow(String str) {
		Toast.makeText(ZxtCardActivity.this, str, Toast.LENGTH_SHORT).show();
		speak(str);
	}

	private void speak(String str) {
		if (mTts != null) {
			try {
				mTts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			exitConfirm();
		}
		return false;
	}

	private void exitConfirm() {
		AlertDialog alertDialog = new AlertDialog.Builder(ZxtCardActivity.this).setTitle("确定要退出程序？").setIcon(android.R.drawable.ic_menu_help).setPositiveButton("确定", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				ZxtCardActivity.this.finish();
			}
		}).setNegativeButton("取消", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				return;
			}
		}).create();
		alertDialog.show();
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		switch (id) {
		case NOCARD:
			return new AlertDialog.Builder(ZxtCardActivity.this).setTitle("警告").setMessage("请插卡...").setIcon(android.R.drawable.ic_dialog_info).create();
		case FINGER1:
			return new AlertDialog.Builder(ZxtCardActivity.this).setTitle("警告").setMessage("请按右手拇指...").setIcon(android.R.drawable.ic_dialog_info).create();
		case FINGER2:
			return new AlertDialog.Builder(ZxtCardActivity.this).setTitle("警告").setMessage("请按左手拇指...").setIcon(android.R.drawable.ic_dialog_info).create();
		case WRITE:
			return new AlertDialog.Builder(ZxtCardActivity.this).setTitle("警告").setMessage("正在写卡，请不要移动卡片...").setIcon(android.R.drawable.ic_dialog_info).create();
		default:
			return null;
		}
	}

	private int bytes2int(byte[] b) {
		int mask = 0xff;
		int temp = 0;
		int res = 0;
		for (int i = 0; i < 4; i++) {
			res <<= 8;
			temp = b[i] & mask;
			res |= temp;
		}
		return res;
	}

	private byte[] int2bytes(int num) {
		byte[] b = new byte[4];
		int mask = 0xff;
		for (int i = 0; i < 4; i++) {
			b[i] = (byte) (num >>> (24 - i * 8));
		}
		return b;
	}

	protected void onDestroy() {
		try {
			Native.close_fm1702();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			lytfingerprint.Close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			_fingerprint.PSCloseDevice();
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}
}