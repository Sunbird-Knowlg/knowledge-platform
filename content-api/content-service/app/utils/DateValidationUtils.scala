package utils

import org.apache.commons.lang3.StringUtils

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
    val startDateString: String = input.get("startDate").asInstanceOf[String]
    val endDateString: String = input.get("endDate").asInstanceOf[String]
    if (StringUtils.isNotBlank(startDateString) && StringUtils.isNotBlank(endDateString)) {
      val startDate: java.util.Date = stringToDateConverter(startDateString)
      val endDate: java.util.Date = stringToDateConverter(endDateString)
      val startTimeString: String = input.get("startTime").asInstanceOf[String]
      val endTimeString: String = input.get("endTime").asInstanceOf[String]
      if (startDate.equals(endDate) && StringUtils.isNotBlank(startTimeString) && StringUtils.isNotBlank(endTimeString)) {
        val startTime: Date = stringToTimeConverter(startTimeString)
        val endTime: Date = stringToTimeConverter(endTimeString)
        return (startTime.after(endTime) || startTime.equals(endTime))
      }
      startDate.after(endDate)
    } else {
      false
    }
  }

  /**
   * Converts String to Date object.
   *
   * @param dateString the date in String format.
   * @return Date in java.util.Date format.
   */
  def stringToDateConverter(dateString: String): java.util.Date = {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    dateFormat.parse(dateString)
  }

  /**
   * Converts Date object to String.
   *
   * @param date the date in java.util.Date format.
   * @return Date in String format.
   */
  def dateToStringConverter(date: java.util.Date): String = {
    val dateFormat: DateFormat = new SimpleDateFormat("yyyy-mm-dd")
    val strDate: String = dateFormat.format(date)
    strDate
  }

  /**
   * Converts String to Date object for Time.
   *
   * @param timeString the date and time in String format.
   * @return Date in java.util.Date format.
   */
  def stringToTimeConverter(timeString: String): java.util.Date = {
    val dateFormat = new SimpleDateFormat("HH:mm:ss")
    dateFormat.parse(timeString);
  }
}

