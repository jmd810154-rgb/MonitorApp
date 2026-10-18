# 📱 ডিভাইস মনিটর অ্যাপ

সুপাবেসের সাথে অ্যান্ড্রয়েড থেকে এসএমএস এবং ছবি মনিটর করার অ্যাপ।

## 📋 প্রয়োজনীয় জিনিস

- Android Studio (নতুন সংস্করণ)
- Java 11 বা তার উপরে
- Supabase অ্যাকাউন্ট
- ন্যূনতম এসডিকে 21 (Android 5.0)

## 🚀 সেটআপ ধাপ

### ১. Supabase সেটআপ

```sql
-- devices টেবিল
CREATE TABLE devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT UNIQUE,
  device_name TEXT,
  last_sync TIMESTAMP,
  created_at TIMESTAMP DEFAULT now()
);

-- sms টেবিল
CREATE TABLE sms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT REFERENCES devices(device_id),
  sender TEXT,
  body TEXT,
  date BIGINT,
  created_at TIMESTAMP DEFAULT now()
);

-- photos টেবিল
CREATE TABLE photos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT REFERENCES devices(device_id),
  photo_name TEXT,
  photo_data BYTEA,
  created_at TIMESTAMP DEFAULT now()
);
```

### ২. অ্যাপ সেটআপ

1. `app/src/main/java/com/monitor/smsphoto/Config.java` খুলো
2. `SUPABASE_URL` এবং `SUPABASE_KEY` দিয়ে প্রতিস্থাপন করো
3. অ্যাপ বিল্ড এবং রান করো

### ৩. ড্যাশবোর্ড সেটআপ

1. `dashboard.html` খুলো
2. একই URL এবং KEY দিয়ে প্রতিস্থাপন করো
3. ব্রাউজারে খুলো

## 📝 ফাইল স্ট্রাকচার

```
MonitorApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/monitor/smsphoto/
│   │   │   ├── MainActivity.java
│   │   │   ├── MonitorService.java
│   │   │   └── Config.java
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       └── values/
│   │           ├── strings.xml
│   │           ├── colors.xml
│   │           └── styles.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 🔧 কাস্টমাইজেশন

### সিঙ্ক ফ্রিকোয়েন্সি পরিবর্তন

`MonitorService.java` এ `60000` (৬০ সেকেন্ড) পরিবর্তন করো:

```java
handler.postDelayed(this, 60000); // মিলিসেকেন্ডে
```

### SMS সংখ্যা পরিবর্তন

`MonitorService.java` এ `LIMIT 50` পরিবর্তন করো:

```java
"date DESC LIMIT 50" // সংখ্যা পরিবর্তন করো
```

## 🔑 Supabase ডেটা কোথায় পাবে

- Supabase Dashboard → SQL Editor
- Tables দেখ: `devices`, `sms`, `photos`
- API → Rest API → URL এবং Key কপি করো

## ⚙️ পারমিশন

অ্যাপ প্রথম চালালে এই পারমিশন চাবে:
- ✅ এসএমএস পড়ার অনুমতি
- ✅ ছবি পড়ার অনুমতি
- ✅ ইন্টারনেট অ্যাক্সেস

সবগুলো অনুমতি দিতে হবে।

## 🌐 ড্যাশবোর্ড ব্যবহার

1. ড্যাশবোর্ড HTML খুলো
2. ড্রপডাউন থেকে ডিভাইস বেছে নাও
3. এসএমএস এবং ছবি দেখ
4. ছবিতে ক্লিক করে বড় করে দেখ

## 🐛 সমস্যা সমাধান

### অ্যাপ ক্র্যাশ হচ্ছে?
- পারমিশন দিয়েছো কিনা চেক করো
- Config.java এ সঠিক URL বসিয়েছো কিনা চেক করো

### Supabase এ ডেটা আসছে না?
- নেটওয়ার্ক চেক করো
- API Key সঠিক কিনা চেক করো
- টেবিল নাম সঠিক কিনা চেক করো

### ড্যাশবোর্ডে তথ্য দেখা যাচ্ছে না?
- SUPABASE_URL এবং SUPABASE_KEY চেক করো
- ব্রাউজার কনসোল খুলে এরর দেখো

## 📄 লাইসেন্স

এটি ব্যক্তিগত ব্যবহারের জন্য।

---

**প্রশ্ন থাকলে আমাকে বলো! ✨**
