import request from './request'

/* ---------------- 认证 ---------------- */
export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const getInfo = () => request.get('/auth/info')
export const changePassword = (data) => request.put('/auth/password', data)
export const register = (data) => request.post('/auth/register', data)

/* ---------------- 公共 ---------------- */
export const getNotices = (limit = 10) => request.get('/public/notices', { params: { limit } })
export const getRules = () => request.get('/public/rules')
export const getServerTime = () => request.get('/system/server-time')
export const getClock = () => request.get('/system/clock')
export const getSystemInfo = () => request.get('/system/info')

/* ---------------- 学生端 ---------------- */
export const studentRooms = (roomType) => request.get('/student/rooms', { params: { roomType } })
export const studentRoomDetail = (roomId) => request.get(`/student/rooms/${roomId}`)
export const reserveSeat = (seatId) => request.post('/student/reserve', null, { params: { seatId } })
export const cancelReserve = (reservationId) => request.post('/student/cancel', null, { params: { reservationId } })
export const checkIn = (reservationId, latitude, longitude) =>
  request.post('/student/checkin', null, { params: { reservationId, latitude, longitude } })
export const checkOut = (reservationId, latitude, longitude) =>
  request.post('/student/checkout', null, { params: { reservationId, latitude, longitude } })
/** 当前生效的签到点（地理围栏） */
export const getCheckinLocation = (roomId) =>
  request.get('/student/checkin-location', { params: { roomId } })
/** 定位校验是否启用 */
export const getLocationEnabled = () => request.get('/student/checkin-location/enabled')
/** 今日预约（含已签退 / 已取消记录） */
export const getTodayReservation = () => request.get('/student/today')
/** 今日仍生效的预约（选座占位判断用） */
export const getTodayActive = () => request.get('/student/today/active')
export const getMyReservations = (params) => request.get('/student/reservations', { params })
export const getStudentClock = () => request.get('/student/clock')
export const getMyLongTerm = () => request.get('/student/long-term/mine')
export const getLongRooms = () => request.get('/student/long-term/rooms')
export const getLongRoomSeats = (roomId) => request.get(`/student/long-term/rooms/${roomId}/seats`)
export const applyLongTerm = (roomId, seatId) =>
  request.post('/student/long-term/apply', null, { params: { roomId, seatId } })
export const cancelLongTerm = (bindingId) =>
  request.post('/student/long-term/cancel', null, { params: { bindingId } })
export const withdrawLongTerm = (bindingId) =>
  request.post('/student/long-term/withdraw', null, { params: { bindingId } })
export const getMyAssessments = () => request.get('/student/long-term/assessments')
export const getMyCalendar = (month) => request.get('/student/long-term/calendar', { params: { month } })
export const getClearCountdown = () => request.get('/student/clear-countdown')

/* ---------------- 管理端 ---------------- */
export const adminOverview = () => request.get('/admin/statistics/overview')
export const adminCharts = (type = 'all', days = 7) =>
  request.get('/admin/statistics/charts', { params: { type, days } })
export const adminDistribution = () => request.get('/admin/statistics/distribution')
export const adminRoomTrend = (days = 7) => request.get('/admin/statistics/room-trend', { params: { days } })

export const adminRooms = (params) => request.get('/admin/rooms', { params })
export const addRoom = (data) => request.post('/admin/rooms', data)
export const updateRoom = (data) => request.put('/admin/rooms', data)
export const deleteRoom = (id) => request.delete(`/admin/rooms/${id}`)
export const generateSeats = (id, count) =>
  request.post(`/admin/rooms/${id}/seats/generate`, null, { params: { count } })

export const adminSeats = (roomId) => request.get(`/admin/rooms/${roomId}/seats`)
export const addSeat = (data) => request.post('/admin/seats', data)
export const updateSeat = (data) => request.put('/admin/seats', data)
export const deleteSeat = (id) => request.delete(`/admin/seats/${id}`)

export const adminReservations = (params) => request.get('/admin/reservations', { params })
export const releaseReservation = (id, reason) =>
  request.post(`/admin/reservations/${id}/release`, null, { params: { reason } })
export const adminCancelReservation = (id) => request.post(`/admin/reservations/${id}/cancel`)

export const adminLongTerm = (params) => request.get('/admin/long-term', { params })
export const adminPendingLongTerm = (params) => request.get('/admin/long-term/pending', { params })
export const reviewLongTerm = (id, approve, remark) =>
  request.post(`/admin/long-term/${id}/review`, null, { params: { approve, remark } })
export const releaseLongTerm = (id, reason) =>
  request.post(`/admin/long-term/${id}/release`, null, { params: { reason } })
export const runAssessment = (month, dryRun) =>
  request.post('/admin/assessment/run', null, { params: { month, dryRun } })
export const adminAssessments = (params) => request.get('/admin/assessment', { params })

export const adminUsers = (params) => request.get('/admin/users', { params })
export const addUser = (data) => request.post('/admin/users', data)
export const updateUser = (data) => request.put('/admin/users', data)
export const deleteUser = (id) => request.delete(`/admin/users/${id}`)
export const resetUserPassword = (id) => request.post(`/admin/users/${id}/reset-password`)
export const toggleUserStatus = (id, status) =>
  request.post(`/admin/users/${id}/status`, null, { params: { status } })

export const adminNotices = (params) => request.get('/admin/notices', { params })
export const addNotice = (data) => request.post('/admin/notices', data)
export const updateNotice = (data) => request.put('/admin/notices', data)
export const deleteNotice = (id) => request.delete(`/admin/notices/${id}`)

export const adminConfigs = () => request.get('/admin/configs')
export const updateConfigs = (data) => request.put('/admin/configs', data)

/* ---------------- 签到点（地理围栏） ---------------- */
export const adminLocations = (keyword) => request.get('/admin/locations', { params: { keyword } })
export const addLocation = (data) => request.post('/admin/locations', data)
export const updateLocation = (data) => request.put('/admin/locations', data)
export const deleteLocation = (id) => request.delete(`/admin/locations/${id}`)
export const toggleLocation = (id, enabled) =>
  request.post(`/admin/locations/${id}/status`, null, { params: { enabled } })
export const locationRooms = () => request.get('/admin/locations/rooms')