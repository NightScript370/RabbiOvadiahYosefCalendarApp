package com.ej.rovadiahyosefcalendar.classes;

public class ZmanimNames {

    private final boolean mIsZmanimInHebrew;
    private final boolean mIsZmanimEnglishTranslated;

    public ZmanimNames(boolean isZmanimInHebrew, boolean isZmanimEnglishTranslated) {
        mIsZmanimInHebrew = isZmanimInHebrew;
        mIsZmanimEnglishTranslated = isZmanimEnglishTranslated;
    }

    public String getChatzotLaylaString() {
        if (mIsZmanimInHebrew) {
            return "חצות הלילה";
        } else if (mIsZmanimEnglishTranslated) {
            return "Midnight";
        } else {
            return "Ḥatzot Layla";
        }
    }

    public String getLChumraString() {
        if (mIsZmanimInHebrew) {
            return "לחומרא";
        } else if (mIsZmanimEnglishTranslated) {
            return "(Stringent)";
        } else {
            return "L'Ḥumra";
        }
    }

    public String getTaanitString() {
        if (mIsZmanimInHebrew) {
            return "תענית";
        } else if (mIsZmanimEnglishTranslated) {
            return "Fast";
        } else {
            return "Ta'anit";
        }
    }

    public String getTzaitHacochavimString() {
        if (mIsZmanimInHebrew) {
            return "צאת הכוכבים";
        } else if (mIsZmanimEnglishTranslated) {
            return "Nightfall";
        } else {
            return "Tzet Hakokhavim";
        }
    }

    public String getSunsetString() {
        if (mIsZmanimInHebrew) {
            return "שקיעה";
        } else if (mIsZmanimEnglishTranslated) {
            return "Sunset";
        } else {
            return "Sheqi'a";
        }
    }

    public String getRTString() {
        if (mIsZmanimInHebrew) {
            return "רבינו תם";
        } else {
            return "Rabbenu Tam";
        }
    }

    public String getMacharString() {
        if (mIsZmanimInHebrew) {
            return " (מחר) ";
        } else {
            return " (Tom) ";
        }
    }

    public String getStartsString() {
        if (mIsZmanimInHebrew) {
            return " מתחיל";
        } else {
            return " Starts";
        }
    }

    public String getEndsString() {
        if (mIsZmanimEnglishTranslated) {
            return " Ends";
        } else {
            return "";
        }
    }

    public String getTzaitString() {
        if (mIsZmanimInHebrew) {
            return "צאת ";
        } else if (!mIsZmanimEnglishTranslated) {
            return "Tzet ";
        } else {
            return "";//if we are translating to English, we don't want to show the word Tzait first, just {Zman} Ends
        }
    }

    public String getCandleLightingString() {
        if (mIsZmanimInHebrew) {
            return "הדלקת נרות";
        } else {
            return "Candle Lighting";
        }
    }

    public String getYalkutYosefString() {
        if (mIsZmanimInHebrew) {
            return "ילקוט יוסף";
        } else {
            return "Yalkut Yosef";
        }
    }

    public String getHalachaBerurahString() {
        if (mIsZmanimInHebrew) {
            return "הלכה ברורה";
        } else {
            return "Halacha Berura";
        }
    }

    public String getAbbreviatedYalkutYosefString() {
        if (mIsZmanimInHebrew) {
            return "י\"י";
        } else {
            return "Y\"Y";
        }
    }

    public String getAbbreviatedHalachaBerurahString() {
        if (mIsZmanimInHebrew) {
            return "ה\"ב";
        } else {
            return "H\"B";
        }
    }

    public String getPlagHaminchaString() {
        if (mIsZmanimInHebrew) {
            return "פלג המנחה";
        } else {
            return "Plag HaMinḥa";
        }
    }

    public String getMinchaKetanaString() {
        if (mIsZmanimInHebrew) {
            return "מנחה קטנה";
        } else {
            return "Minḥa Ketana";
        }
    }

    public String getMinchaGedolaString() {
        if (mIsZmanimInHebrew) {
            return "מנחה גדולה";
        } else if (mIsZmanimEnglishTranslated) {
            return "Earliest Minḥa";
        } else {
            return "Minḥa Gedola";
        }
    }

    public String getChatzotString() {
        if (mIsZmanimInHebrew) {
            return "חצות";
        } else if (mIsZmanimEnglishTranslated) {
            return "Mid-day";
        } else {
            return "Ḥatzot";
        }
    }

    public String getBiurChametzString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן ביעור חמץ";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest time to burn Chametz";
        } else {
            return "Sof Zeman Biur Ḥametz";
        }
    }

    public String getBrachotShmaString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן ברכות שמע";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Berakhot Shema";
        } else {
            return "Sof Zman Berakhot Shema";
        }
    }

    public String getAchilatChametzString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן אכילת חמץ";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest time to eat Ḥametz";
        } else {
            return "Sof Zeman Akhilat Ḥametz";
        }
    }

    public String getBirkatHachamaString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן ברכת החמה";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Birkat HaChamah";
        } else {
            return "Sof Zeman Birkat HaChamah";
        }
    }

    public String getShmaGraString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן שמע גר\"א";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Shema GR\"A";
        } else {
            return "Sof Zman Shema GR\"A";
        }
    }

    public String getShmaMgaString() {
        if (mIsZmanimInHebrew) {
            return "סוף זמן שמע מג\"א";
        } else if (mIsZmanimEnglishTranslated) {
            return "Latest Shema MG\"A";
        } else {
            return "Sof Zman Shema MG\"A";
        }
    }

    public String getMishorString() {
        if (mIsZmanimInHebrew) {
            return "מישור";
        } else if (mIsZmanimEnglishTranslated) {
            return "Sea Level";
        } else {
            return "Mishor";
        }
    }

    public String getBetterString() {
        if (mIsZmanimInHebrew) {
            return "(העדיף)";
        } else {
            return "(Better)";
        }
    }

    public String getElevatedString() {
        if (mIsZmanimInHebrew) {
            return "(גבוה)";
        } else {
            return "(Elevated)";
        }
    }

    public String getHaNetzString() {
        if (mIsZmanimInHebrew) {
            return "הנץ";
        } else if (mIsZmanimEnglishTranslated) {
            return "Sunrise";
        } else {
            return "HaNetz";
        }
    }

    public String getIsInString() {
        if (mIsZmanimInHebrew) {
            return " ב...";
        } else {
            return " is in...";
        }
    }

    public String getTalitTefilinString() {
        if (mIsZmanimInHebrew) {
            return "טלית ותפילין";
        } else {
            return "Earliest Talit/Tefilin";
        }
    }

    public String getAlotString() {
        if (mIsZmanimInHebrew) {
            return "עלות השחר";
        } else if (mIsZmanimEnglishTranslated) {
            return "Dawn";
        } else {
            return "Alot Hashachar";
        }
    }
}
