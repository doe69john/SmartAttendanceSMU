import { format, parseISO } from 'date-fns';

import { APP_CONFIG } from '@/config/constants';

export const CAMPUS_LOCALE = APP_CONFIG.locale ?? 'en-SG';
export const CAMPUS_TIME_ZONE = APP_CONFIG.timeZone ?? 'Asia/Singapore';
const TIME_ONLY_PATTERN = /^\d{1,2}:\d{2}(?::\d{2})?$/;

const campusTimeFormatter = new Intl.DateTimeFormat(CAMPUS_LOCALE, {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: CAMPUS_TIME_ZONE,
});

const campusDateFormatter = new Intl.DateTimeFormat(CAMPUS_LOCALE, {
  dateStyle: 'medium',
  timeZone: CAMPUS_TIME_ZONE,
});

const campusDateTimeFormatter = new Intl.DateTimeFormat(CAMPUS_LOCALE, {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: CAMPUS_TIME_ZONE,
});

const campusDateWithWeekdayFormatter = new Intl.DateTimeFormat(CAMPUS_LOCALE, {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  timeZone: CAMPUS_TIME_ZONE,
});

const parseTimeInput = (value: string) => {
  const normalized = value.length === 5 ? `${value}:00` : value;
  return parseISO(`1970-01-01T${normalized}`);
};

export const formatCampusTime = (value?: string | null, fallbackLabel = 'N/A') => {
  if (!value) {
    return fallbackLabel;
  }
  const trimmed = value.trim();
  const isTimeOnly = TIME_ONLY_PATTERN.test(trimmed);
  try {
    const parsed = isTimeOnly ? parseTimeInput(trimmed) : parseISO(trimmed);
    if (isTimeOnly) {
      return format(parsed, 'p');
    }
    return campusTimeFormatter.format(parsed);
  } catch (error) {
    console.warn('Unable to format time', error);
    return fallbackLabel;
  }
};

export const formatCampusDate = (value?: string | null, fallbackLabel = 'N/A') => {
  if (!value) {
    return fallbackLabel;
  }
  try {
    const parsed = parseISO(value);
    if (value.includes('T')) {
      return campusDateFormatter.format(parsed);
    }
    return format(parsed, 'PP');
  } catch (error) {
    console.warn('Unable to format date', error);
    return fallbackLabel;
  }
};

export const formatCampusDateTime = (value?: string | null, fallbackLabel = 'N/A') => {
  if (!value) {
    return fallbackLabel;
  }
  try {
    const parsed = parseISO(value);
    return campusDateTimeFormatter.format(parsed);
  } catch (error) {
    console.warn('Unable to format date time', error);
    return fallbackLabel;
  }
};

export const formatCampusDateWithWeekday = (value?: string | null, fallbackLabel = 'N/A') => {
  if (!value) {
    return fallbackLabel;
  }
  try {
    const parsed = parseISO(value);
    return campusDateWithWeekdayFormatter.format(parsed);
  } catch (error) {
    console.warn('Unable to format date with weekday', error);
    return fallbackLabel;
  }
};
