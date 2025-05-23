/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf.exception;

public class InvalidObjectWafException extends AbstractWafException {
  public InvalidObjectWafException(int errorCode) {
    super("Invalid object", errorCode);
  }
}
