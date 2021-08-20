package utils

import java.text.{DateFormat, SimpleDateFormat}
import java.util.Date

object DateValidationUtils {

  /**
   * Validates whether the end date/time is greater than start date/time.
   *
   * @param input the Event Body.
   * @return true : If endDate is > startDate; false : otherwise.
   */
  def validateDatesAndTimes(input: java.util.Map[String, AnyRef]): Boolean = {

    val startDateString : String = input.getOrDefault("startDate", dateToStringConverter(new Date())).toString;
    val startDate : java.util.Date = stringToDateConverter(startDateString);
    val endDateString : String = input.getOrDefault("endDate", dateToStringConverter(new Date())).toString;
    val endDate : java.util.Date = stringToDateConverter(endDateString);

    if (startDate.equals(endDate)) {
      val startTimeString : String = input.getOrDefault("startTime", "00:00:00+00:00").toString;
      val startTime : Date = stringToTimeConverter(startTimeString);
      val endTimeString : String = input.getOrDefault("endTime", "00:00:00+00:00").toString;
      val endTime : Date = stringToTimeConverter(endTimeString);
      return (startTime.after(endTime) || startTime.equals(endTime));
    }
    return startDate.after(endDate);
  }

  /**
   * Converts String to Date object.
   *
   * @param dateString the date in String format.
   * @return Date in java.util.Date format.
   */
  def stringToDateConverter(dateString : String): java.util.Date ={

    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    dateFormat.parse(dateString);
  }

  /**
   * Converts Date object to String.
   *
   * @param date the date in java.util.Date format.
   * @return Date in String format.
   */
  def dateToStringConverter(date : java.util.Date): String ={

    val dateFormat : DateFormat = new SimpleDateFormat("yyyy-mm-dd");
    val strDate : String = dateFormat.format(date);
    return strDate;
  }

  /**
   * Converts String to Date object for Time.
   *
   * @param timeString the date and time in String format.
   * @return Date in java.util.Date format.
   */
  def stringToTimeConverter(timeString : String): java.util.Date ={

    val dateFormat = new SimpleDateFormat("HH:mm:ss")
    dateFormat.parse(timeString);
  }
}

